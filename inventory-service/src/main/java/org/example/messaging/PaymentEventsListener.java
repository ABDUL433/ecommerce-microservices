package org.example.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.exception.ResourceNotFoundException;
import org.example.service.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventsListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventsListener.class);

    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;

    public PaymentEventsListener(InventoryService inventoryService, ObjectMapper objectMapper) {
        this.inventoryService = inventoryService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "payment-events", groupId = "inventory-service")
    public void onPaymentEvent(String payload) {
        PaymentEvent event;
        try {
            event = objectMapper.readValue(payload, PaymentEvent.class);
        } catch (JsonProcessingException e) {
            log.error("Skipping malformed message: {}", payload, e);
            return;
        }

        try {
            if (PaymentEvent.SUCCEEDED.equals(event.eventType())) {
                inventoryService.commit(event.orderId()); // stock leaves for good
                log.info("Stock committed for order {}", event.orderId());
            } else if (PaymentEvent.FAILED.equals(event.eventType())) {
                inventoryService.release(event.orderId()); // saga compensation
                log.info("Stock released for order {}", event.orderId());
            }
        } catch (ResourceNotFoundException e) {
            log.warn("No reservations to {} for order {}", event.eventType(), event.orderId());
        }
    }
}
