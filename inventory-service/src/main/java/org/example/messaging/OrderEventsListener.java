package org.example.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.example.exception.InsufficientStockException;
import org.example.exception.ResourceNotFoundException;
import org.example.service.InventoryService;
import org.example.service.ReservationLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderEventsListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsListener.class);

    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public OrderEventsListener(InventoryService inventoryService,
                               ObjectMapper objectMapper,
                               MeterRegistry meterRegistry) {
        this.inventoryService = inventoryService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(topics = "order-events", groupId = "inventory-service")
    public void onOrderEvent(String payload) {
        OrderCreatedEvent event;
        try {
            event = objectMapper.readValue(payload, OrderCreatedEvent.class);
        } catch (JsonProcessingException e) {
            log.error("Skipping malformed message: {}", payload, e);
            return;
        }
        if (!OrderCreatedEvent.TYPE.equals(event.eventType())) {
            return; // not an event this service handles
        }

        List<ReservationLine> lines = event.items().stream()
                .map(i -> new ReservationLine(i.productId(), i.quantity()))
                .toList();
        try {
            // on success this also writes a StockReserved outbox row in the same transaction
            inventoryService.reserveOrder(event.orderId(), lines);
            count("reserved");
            log.info("Reserved stock for order {}", event.orderId());
        } catch (InsufficientStockException | ResourceNotFoundException e) {
            // business failure: nothing stays reserved. Tell the Order Service via a StockFailed event.
            count("rejected");
            log.warn("Could not reserve stock for order {}: {}", event.orderId(), e.getMessage());
            inventoryService.recordStockFailed(event.orderId(), e.getMessage());
        }
    }

    private void count(String result) {
        Counter.builder("inventory.reservations")
                .description("Stock reservation attempts by result")
                .tag("result", result)
                .register(meterRegistry)
                .increment();
    }
}
