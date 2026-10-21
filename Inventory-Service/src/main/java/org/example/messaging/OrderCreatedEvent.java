package org.example.messaging;

import java.util.List;
import java.util.UUID;

/** The part of the OrderCreated event this service needs. Unknown JSON fields are ignored. */
public record OrderCreatedEvent(
        String eventType,
        UUID orderId,
        UUID customerId,
        List<Item> items) {

    public static final String TYPE = "OrderCreated";

    public record Item(UUID productId, int quantity) {
    }
}

