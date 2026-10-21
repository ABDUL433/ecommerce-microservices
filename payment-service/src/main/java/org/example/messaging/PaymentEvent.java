package org.example.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Result of a payment. Published on the payment-events topic. */
public record PaymentEvent(String eventType, UUID orderId, BigDecimal amount, String reason, Instant occurredAt) {

    public static final String SUCCEEDED = "PaymentSucceeded";
    public static final String FAILED = "PaymentFailed";

    public static PaymentEvent succeeded(UUID orderId, BigDecimal amount) {
        return new PaymentEvent(SUCCEEDED, orderId, amount, null, Instant.now());
    }

    public static PaymentEvent failed(UUID orderId, BigDecimal amount, String reason) {
        return new PaymentEvent(FAILED, orderId, amount, reason, Instant.now());
    }
}
