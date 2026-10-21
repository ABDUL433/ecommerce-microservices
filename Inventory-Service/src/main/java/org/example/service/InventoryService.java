package org.example.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.InventoryResponse;
import org.example.dto.ReservationResponse;
import org.example.entity.Inventory;
import org.example.entity.Outbox;
import org.example.entity.ReservationStatus;
import org.example.entity.StockReservation;
import org.example.exception.ResourceNotFoundException;
import org.example.messaging.StockEvent;
import org.example.repository.InventoryRepository;
import org.example.repository.OutboxRepository;
import org.example.repository.StockReservationRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Write methods use a TransactionTemplate inside @Retryable on purpose: the
 * optimistic-lock failure is raised when the transaction COMMITS, so the retry
 * must wrap the whole transaction. Putting @Retryable and @Transactional on the
 * same method does not guarantee that order.
 */
@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final StockReservationRepository reservationRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;

    public InventoryService(InventoryRepository inventoryRepository,
                            StockReservationRepository reservationRepository,
                            OutboxRepository outboxRepository,
                            ObjectMapper objectMapper,
                            TransactionTemplate tx) {
        this.inventoryRepository = inventoryRepository;
        this.reservationRepository = reservationRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.tx = tx;
    }

    @Transactional(readOnly = true)
    public InventoryResponse getInventory(UUID productId) {
        return InventoryResponse.from(findInventory(productId));
    }

    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttempts = 3, backoff = @Backoff(delay = 50))
    public InventoryResponse addStock(UUID productId, int qty) {
        return tx.execute(txStatus -> {
            Inventory inv = findInventory(productId);
            inv.addStock(qty);
            return InventoryResponse.from(inv);
        });
    }

    /**
     * REST test endpoint. Idempotent: the same (orderId, productId) returns the existing reservation.
     * DataIntegrityViolationException is retried because two concurrent duplicates
     * hit the unique constraint; the retry then finds the existing row.
     */
    @Retryable(retryFor = {ObjectOptimisticLockingFailureException.class, DataIntegrityViolationException.class},
            maxAttempts = 3, backoff = @Backoff(delay = 50))
    public ReservationResponse reserve(UUID orderId, UUID productId, int qty) {
        return tx.execute(txStatus -> ReservationResponse.from(reserveInTx(orderId, productId, qty)));
    }

    /**
     * Reserves ALL lines of an order in ONE transaction (all or nothing). If any line has
     * too little stock, InsufficientStockException is thrown and nothing stays reserved.
     * When something was newly reserved, a StockReserved outbox row is written in the SAME
     * transaction. A repeated call for the same order reserves nothing and writes no event.
     */
    @Retryable(retryFor = {ObjectOptimisticLockingFailureException.class, DataIntegrityViolationException.class},
            maxAttempts = 3, backoff = @Backoff(delay = 50))
    public void reserveOrder(UUID orderId, List<ReservationLine> lines) {
        Map<UUID, Integer> qtyByProduct = new LinkedHashMap<>();
        for (ReservationLine line : lines) {
            qtyByProduct.merge(line.productId(), line.quantity(), Integer::sum);
        }
        tx.executeWithoutResult(txStatus -> {
            boolean newlyReserved = false;
            for (Map.Entry<UUID, Integer> line : qtyByProduct.entrySet()) {
                UUID productId = line.getKey();
                if (reservationRepository.findByOrderIdAndProductId(orderId, productId).isEmpty()) {
                    reserveNew(orderId, productId, line.getValue());
                    newlyReserved = true;
                }
            }
            if (newlyReserved) {
                outboxRepository.save(new Outbox(orderId, StockEvent.RESERVED,
                        toJson(StockEvent.reserved(orderId))));
            }
        });
    }

    /** Called after a failed reservation (its transaction was rolled back). Writes a StockFailed outbox row. */
    public void recordStockFailed(UUID orderId, String reason) {
        tx.executeWithoutResult(txStatus ->
                outboxRepository.save(new Outbox(orderId, StockEvent.FAILED,
                        toJson(StockEvent.failed(orderId, reason)))));
    }

    /** Saga compensation (payment failed). Only RESERVED rows change, so repeats are harmless. */
    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttempts = 3, backoff = @Backoff(delay = 50))
    public List<ReservationResponse> release(UUID orderId) {
        return tx.execute(txStatus -> {
            List<StockReservation> reservations = findReservations(orderId);
            for (StockReservation r : reservations) {
                if (r.getStatus() == ReservationStatus.RESERVED) {
                    findInventory(r.getProductId()).release(r.getQty());
                    r.setStatus(ReservationStatus.RELEASED);
                }
            }
            return reservations.stream().map(ReservationResponse::from).toList();
        });
    }

    /** Payment succeeded: stock leaves permanently. Only RESERVED rows change. */
    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttempts = 3, backoff = @Backoff(delay = 50))
    public List<ReservationResponse> commit(UUID orderId) {
        return tx.execute(txStatus -> {
            List<StockReservation> reservations = findReservations(orderId);
            for (StockReservation r : reservations) {
                if (r.getStatus() == ReservationStatus.RESERVED) {
                    findInventory(r.getProductId()).commit(r.getQty());
                    r.setStatus(ReservationStatus.COMMITTED);
                }
            }
            return reservations.stream().map(ReservationResponse::from).toList();
        });
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getReservations(UUID orderId) {
        return findReservations(orderId).stream().map(ReservationResponse::from).toList();
    }

    /** Must run inside a transaction. Returns the existing reservation or creates a new one. */
    private StockReservation reserveInTx(UUID orderId, UUID productId, int qty) {
        Optional<StockReservation> existing =
                reservationRepository.findByOrderIdAndProductId(orderId, productId);
        if (existing.isPresent()) {
            return existing.get();
        }
        return reserveNew(orderId, productId, qty);
    }

    /** Must run inside a transaction. */
    private StockReservation reserveNew(UUID orderId, UUID productId, int qty) {
        Inventory inv = findInventory(productId);
        inv.reserve(qty);
        return reservationRepository.save(
                new StockReservation(orderId, productId, qty, ReservationStatus.RESERVED));
    }

    private Inventory findInventory(UUID productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for product: " + productId));
    }

    private List<StockReservation> findReservations(UUID orderId) {
        List<StockReservation> reservations = reservationRepository.findByOrderId(orderId);
        if (reservations.isEmpty()) {
            throw new ResourceNotFoundException("No reservations found for order: " + orderId);
        }
        return reservations;
    }

    private String toJson(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize event", e);
        }
    }
}
