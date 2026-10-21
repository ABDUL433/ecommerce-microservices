package org.example.messaging;

import java.util.UUID;

/** Result event from the Inventory Service. Unknown JSON fields are ignored. */
public record InventoryEvent(String eventType, UUID orderId, String reason) {

    public static final String STOCK_RESERVED = "StockReserved";
    public static final String STOCK_FAILED = "StockFailed";
}
