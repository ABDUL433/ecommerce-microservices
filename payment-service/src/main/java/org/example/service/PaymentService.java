package org.example.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.PaymentResponse;
import org.example.entity.Outbox;
import org.example.entity.Payment;
import org.example.entity.PaymentStatus;
import org.example.exception.ResourceNotFoundException;
import org.example.gateway.GatewayResult;
import org.example.gateway.PaymentGateway;
import org.example.messaging.PaymentEvent;
import org.example.repository.OutboxRepository;
import org.example.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final OutboxRepository outboxRepository;
    private final PaymentGateway gateway;
    private final ObjectMapper objectMapper;

    public PaymentService(PaymentRepository paymentRepository,
                          OutboxRepository outboxRepository,
                          PaymentGateway gateway,
                          ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.outboxRepository = outboxRepository;
        this.gateway = gateway;
        this.objectMapper = objectMapper;
    }

    /**
     * Idempotent: an order is charged at most once (UNIQUE order_id). The payment row and the
     * PaymentSucceeded/PaymentFailed outbox row are saved in the SAME transaction.
     * Note: with a real provider you would call it outside the DB transaction and pass an
     * idempotency key; the fake gateway has no side effects, so this is fine here.
     */
    @Transactional
    public void processPayment(UUID orderId, UUID customerId, BigDecimal amount) {
        if (paymentRepository.existsByOrderId(orderId)) {
            log.info("Payment for order {} already processed, ignoring duplicate request", orderId);
            return;
        }

        GatewayResult result = gateway.charge(orderId, amount);
        PaymentStatus status = result.success() ? PaymentStatus.SUCCESS : PaymentStatus.FAILED;
        paymentRepository.save(new Payment(orderId, customerId, amount, status, result.failureReason()));

        PaymentEvent event = result.success()
                ? PaymentEvent.succeeded(orderId, amount)
                : PaymentEvent.failed(orderId, amount, result.failureReason());
        outboxRepository.save(new Outbox(orderId, event.eventType(), toJson(event)));
        log.info("Payment {} for order {}", status, orderId);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getByOrderId(UUID orderId) {
        return paymentRepository.findByOrderId(orderId)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("No payment for order: " + orderId));
    }

    private String toJson(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize event", e);
        }
    }
}
