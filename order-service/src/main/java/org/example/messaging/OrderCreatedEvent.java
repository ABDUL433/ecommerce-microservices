package org.example.messaging;

import org.example.entity.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderCreatedEvent(
        String eventType,
        UUID orderId,
        UUID customerId,
        List<Item> items,
        Instant occurredAt) {

    public static final String TYPE = "OrderCreated";

    public record Item(UUID productId, int quantity, BigDecimal unitPrice) {
    }

    public static OrderCreatedEvent from(Order order) {
        List<Item> items = order.getItems().stream()
                .map(i -> new Item(i.getProductId(), i.getQuantity(), i.getUnitPrice()))
                .toList();
        return new OrderCreatedEvent(TYPE, order.getId(), order.getCustomerId(), items, Instant.now());
    }
}

