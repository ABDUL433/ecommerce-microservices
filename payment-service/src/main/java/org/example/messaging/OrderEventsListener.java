package org.example.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventsListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsListener.class);

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    public OrderEventsListener(PaymentService paymentService, ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "order-events", groupId = "payment-service")
    public void onOrderEvent(String payload) {
        PaymentRequestedEvent event;
        try {
            event = objectMapper.readValue(payload, PaymentRequestedEvent.class);
        } catch (JsonProcessingException e) {
            log.error("Skipping malformed message: {}", payload, e);
            return;
        }
        if (!PaymentRequestedEvent.TYPE.equals(event.eventType())) {
            return; // e.g. OrderCreated: not for this service
        }

        try {
            paymentService.processPayment(event.orderId(), event.customerId(), event.amount());
        } catch (DataIntegrityViolationException e) {
            // two copies of the same event raced: the other one already stored the payment and its outbox row
            log.info("Payment for order {} was already processed (duplicate event)", event.orderId());
        }
    }
}
