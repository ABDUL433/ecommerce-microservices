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
public class PaymentEventsListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventsListener.class);

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    public PaymentEventsListener(OrderService orderService, ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "payment-events", groupId = "order-service")
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
                orderService.confirmOrder(event.orderId());
                log.info("Order {} CONFIRMED", event.orderId());
            } else if (PaymentEvent.FAILED.equals(event.eventType())) {
                orderService.cancelOrder(event.orderId());
                log.info("Order {} CANCELLED: {}", event.orderId(), event.reason());
            }
        } catch (ResourceNotFoundException | IllegalStateException e) {
            // unknown order or invalid state change: retrying would not help
            log.warn("Could not apply {} to order {}: {}", event.eventType(), event.orderId(), e.getMessage());
        }
    }
}

