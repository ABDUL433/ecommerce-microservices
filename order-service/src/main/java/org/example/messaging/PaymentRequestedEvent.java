package org.example.messaging;

import org.example.entity.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Published on order-events once stock is reserved. The Payment Service charges the amount. */
public record PaymentRequestedEvent(
        String eventType,
        UUID orderId,
        UUID customerId,
        BigDecimal amount,
        Instant occurredAt) {

    public static final String TYPE = "PaymentRequested";

    public static PaymentRequestedEvent of(Order order) {
        return new PaymentRequestedEvent(TYPE, order.getId(), order.getCustomerId(),
                order.getTotalAmount(), Instant.now());
    }
}
