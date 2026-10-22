package org.example.dto;

import org.example.entity.Inventory;

import java.util.UUID;

public record InventoryResponse(UUID productId, int availableQty, int reservedQty) {

    public static InventoryResponse from(Inventory inv) {
        return new InventoryResponse(inv.getProductId(), inv.getAvailableQty(), inv.getReservedQty());
    }
}
