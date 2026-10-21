package org.example.messaging;

import java.time.Instant;
import java.util.UUID;

/** Result of trying to reserve stock for an order. Published on the inventory-events topic. */
public record StockEvent(String eventType, UUID orderId, String reason, Instant occurredAt) {

    public static final String RESERVED = "StockReserved";
    public static final String FAILED = "StockFailed";

    public static StockEvent reserved(UUID orderId) {
        return new StockEvent(RESERVED, orderId, null, Instant.now());
    }

    public static StockEvent failed(UUID orderId, String reason) {
        return new StockEvent(FAILED, orderId, reason, Instant.now());
    }
}
