package org.example.dto;

import org.example.entity.ReservationStatus;
import org.example.entity.StockReservation;

import java.util.UUID;

public record ReservationResponse(UUID orderId, UUID productId, int qty, ReservationStatus status) {

    public static ReservationResponse from(StockReservation r) {
        return new ReservationResponse(r.getOrderId(), r.getProductId(), r.getQty(), r.getStatus());
    }
}
