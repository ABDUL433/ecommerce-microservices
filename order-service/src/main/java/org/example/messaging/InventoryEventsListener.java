package org.example.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.exception.ResourceNotFoundException;
import org.example.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class InventoryEventsListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventsListener.class);

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    public InventoryEventsListener(OrderService orderService, ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "inventory-events", groupId = "order-service")
    public void onInventoryEvent(String payload) {
        InventoryEvent event;
        try {
            event = objectMapper.readValue(payload, InventoryEvent.class);
        } catch (JsonProcessingException e) {
            log.error("Skipping malformed message: {}", payload, e);
            return;
        }

        try {
            if (InventoryEvent.STOCK_FAILED.equals(event.eventType())) {
                orderService.cancelOrder(event.orderId());
                log.info("Order {} cancelled: {}", event.orderId(), event.reason());
            } else if (InventoryEvent.STOCK_RESERVED.equals(event.eventType())) {
                // writes a PaymentRequested outbox row; the Payment Service takes it from there
                orderService.requestPayment(event.orderId());
                log.info("Stock reserved for order {}, payment requested", event.orderId());
            }
        } catch (ResourceNotFoundException | IllegalStateException e) {
            // unknown order or order no longer PENDING: retrying would not help
            log.warn("Could not apply {} to order {}: {}", event.eventType(), event.orderId(), e.getMessage());
        }
    }
}
