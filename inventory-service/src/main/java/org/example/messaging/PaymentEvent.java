package org.example.messaging;

import java.util.UUID;

/** Result event from the Payment Service. Unknown JSON fields are ignored. */
public record PaymentEvent(String eventType, UUID orderId, String reason) {

    public static final String SUCCEEDED = "PaymentSucceeded";
    public static final String FAILED = "PaymentFailed";
}

