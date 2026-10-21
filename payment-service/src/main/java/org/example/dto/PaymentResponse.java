package org.example.dto;

import org.example.entity.Payment;
import org.example.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID orderId,
        UUID customerId,
        BigDecimal amount,
        PaymentStatus status,
        String failureReason,
        Instant createdAt) {

    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(p.getOrderId(), p.getCustomerId(), p.getAmount(),
                p.getStatus(), p.getFailureReason(), p.getCreatedAt());
    }
}
