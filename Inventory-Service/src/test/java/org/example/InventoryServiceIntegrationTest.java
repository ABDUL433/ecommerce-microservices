package org.example;

import org.example.entity.Inventory;
import org.example.entity.ReservationStatus;
import org.example.exception.InsufficientStockException;
import org.example.repository.OutboxRepository;
import org.example.service.InventoryService;
import org.example.service.ReservationLine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private OutboxRepository outboxRepository;

    private long outboxCount(UUID orderId, String eventType) {
        return outboxRepository.findAll().stream()
                .filter(o -> o.getAggregateId().equals(orderId) && o.getEventType().equals(eventType))
                .count();
    }

    @Test
    void reserveOrder_reservesStock_andWritesStockReservedEvent() {
        UUID productId = newProduct(10);
        UUID orderId = UUID.randomUUID();

        inventoryService.reserveOrder(orderId, List.of(new ReservationLine(productId, 3)));

        Inventory inv = inventoryOf(productId);
        assertThat(inv.getAvailableQty()).isEqualTo(7);
        assertThat(inv.getReservedQty()).isEqualTo(3);
        assertThat(reservationRepository.findByOrderId(orderId)).hasSize(1);
        assertThat(outboxCount(orderId, "StockReserved")).isEqualTo(1);
    }

    @Test
    void reserveOrder_isIdempotent() {
        UUID productId = newProduct(10);
        UUID orderId = UUID.randomUUID();
        List<ReservationLine> lines = List.of(new ReservationLine(productId, 3));

        inventoryService.reserveOrder(orderId, lines);
        inventoryService.reserveOrder(orderId, lines); // same event delivered twice

        Inventory inv = inventoryOf(productId);
        assertThat(inv.getAvailableQty()).isEqualTo(7);
        assertThat(inv.getReservedQty()).isEqualTo(3);
        assertThat(outboxCount(orderId, "StockReserved")).isEqualTo(1);
    }

    @Test
    void reserveOrder_isAllOrNothing() {
        UUID plenty = newProduct(5);
        UUID scarce = newProduct(1);
        UUID orderId = UUID.randomUUID();

        assertThatThrownBy(() -> inventoryService.reserveOrder(orderId, List.of(
                new ReservationLine(plenty, 1),
                new ReservationLine(scarce, 5))))
                .isInstanceOf(InsufficientStockException.class);

        assertThat(inventoryOf(plenty).getAvailableQty()).isEqualTo(5);
        assertThat(inventoryOf(plenty).getReservedQty()).isZero();
        assertThat(inventoryOf(scarce).getAvailableQty()).isEqualTo(1);
        assertThat(reservationRepository.findByOrderId(orderId)).isEmpty();
        assertThat(outboxCount(orderId, "StockReserved")).isZero();
    }

    @Test
    void release_returnsStock_andIsRepeatable() {
        UUID productId = newProduct(10);
        UUID orderId = UUID.randomUUID();
        inventoryService.reserveOrder(orderId, List.of(new ReservationLine(productId, 3)));

        inventoryService.release(orderId);
        inventoryService.release(orderId); // repeating must not add stock twice

        Inventory inv = inventoryOf(productId);
        assertThat(inv.getAvailableQty()).isEqualTo(10);
        assertThat(inv.getReservedQty()).isZero();
        assertThat(reservationRepository.findByOrderIdAndProductId(orderId, productId).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.RELEASED);
    }

    @Test
    void commit_removesStockForGood_andIsRepeatable() {
        UUID productId = newProduct(10);
        UUID orderId = UUID.randomUUID();
        inventoryService.reserveOrder(orderId, List.of(new ReservationLine(productId, 3)));

        inventoryService.commit(orderId);
        inventoryService.commit(orderId);

        Inventory inv = inventoryOf(productId);
        assertThat(inv.getAvailableQty()).isEqualTo(7);
        assertThat(inv.getReservedQty()).isZero();
        assertThat(reservationRepository.findByOrderIdAndProductId(orderId, productId).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.COMMITTED);
    }

    /**
     * 10 customers fight for 5 units at the same moment. Some may lose to a version conflict
     * after their retries, which is allowed; what must NEVER happen is selling more than exists.
     */
    @Test
    void concurrentReservations_neverOversell() throws Exception {
        int stock = 5;
        int threads = 10;
        UUID productId = newProduct(stock);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            UUID orderId = UUID.randomUUID();
            results.add(pool.submit(() -> {
                start.await();
                try {
                    inventoryService.reserveOrder(orderId, List.of(new ReservationLine(productId, 1)));
                    return "OK";
                } catch (InsufficientStockException e) {
                    return "NO_STOCK";
                } catch (ObjectOptimisticLockingFailureException e) {
                    return "LOCK_CONFLICT";
                }
            }));
        }
        start.countDown();

        int ok = 0;
        int noStock = 0;
        int conflict = 0;
        for (Future<String> result : results) {
            // any other exception surfaces here as an ExecutionException and fails the test
            switch (result.get(60, TimeUnit.SECONDS)) {
                case "OK" -> ok++;
                case "NO_STOCK" -> noStock++;
                default -> conflict++;
            }
        }
        pool.shutdown();

        Inventory inv = inventoryOf(productId);
        assertThat(ok).isBetween(1, stock);
        assertThat(inv.getReservedQty()).isEqualTo(ok);
        assertThat(inv.getAvailableQty() + inv.getReservedQty()).isEqualTo(stock);
        assertThat(ok + noStock + conflict).isEqualTo(threads);
    }
}

