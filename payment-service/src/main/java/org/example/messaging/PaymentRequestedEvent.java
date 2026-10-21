package org.example.messaging;

import java.math.BigDecimal;
import java.util.UUID;

/** Incoming event from the Order Service. Unknown JSON fields are ignored. */
public record PaymentRequestedEvent(String eventType, UUID orderId, UUID customerId, BigDecimal amount) {

    public static final String TYPE = "PaymentRequested";
}
