package org.example;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.example.entity.ReservationStatus;
import org.example.service.InventoryService;
import org.example.service.ReservationLine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Sends real events through a real Kafka broker and checks what the Inventory Service does with them. */
class InventoryKafkaFlowTest extends AbstractIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private InventoryService inventoryService;

    @Test
    void orderCreated_reservesStock_andPublishesStockReserved() throws Exception {
        UUID productId = newProduct(10);
        UUID orderId = UUID.randomUUID();

        send("order-events", orderId, orderCreatedJson(orderId, productId, 2));

        await("reservation to be created",
                () -> reservationRepository.findByOrderIdAndProductId(orderId, productId).isPresent());
        assertThat(inventoryOf(productId).getAvailableQty()).isEqualTo(8);
        assertThat(inventoryOf(productId).getReservedQty()).isEqualTo(2);
        assertThat(awaitInventoryEvent(orderId)).contains("\"eventType\":\"StockReserved\"");
    }

    @Test
    void orderCreated_withTooLittleStock_publishesStockFailed_andReservesNothing() throws Exception {
        UUID productId = newProduct(1);
        UUID orderId = UUID.randomUUID();

        send("order-events", orderId, orderCreatedJson(orderId, productId, 5));

        assertThat(awaitInventoryEvent(orderId)).contains("\"eventType\":\"StockFailed\"");
        assertThat(reservationRepository.findByOrderId(orderId)).isEmpty();
        assertThat(inventoryOf(productId).getAvailableQty()).isEqualTo(1);
        assertThat(inventoryOf(productId).getReservedQty()).isZero();
    }

    @Test
    void paymentFailed_releasesReservedStock() throws Exception {
        UUID productId = newProduct(10);
        UUID orderId = UUID.randomUUID();
        inventoryService.reserveOrder(orderId, List.of(new ReservationLine(productId, 3)));

        send("payment-events", orderId,
                "{\"eventType\":\"PaymentFailed\",\"orderId\":\"" + orderId + "\",\"reason\":\"declined\"}");

        await("stock to be released", () -> inventoryOf(productId).getReservedQty() == 0);
        assertThat(inventoryOf(productId).getAvailableQty()).isEqualTo(10);
        assertThat(reservationRepository.findByOrderIdAndProductId(orderId, productId).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.RELEASED);
    }

    @Test
    void paymentSucceeded_commitsReservedStock() throws Exception {
        UUID productId = newProduct(10);
        UUID orderId = UUID.randomUUID();
        inventoryService.reserveOrder(orderId, List.of(new ReservationLine(productId, 3)));

        send("payment-events", orderId,
                "{\"eventType\":\"PaymentSucceeded\",\"orderId\":\"" + orderId + "\"}");

        await("reservation to be committed", () ->
                reservationRepository.findByOrderIdAndProductId(orderId, productId).orElseThrow().getStatus()
                        == ReservationStatus.COMMITTED);
        assertThat(inventoryOf(productId).getAvailableQty()).isEqualTo(7);
        assertThat(inventoryOf(productId).getReservedQty()).isZero();
    }

    private void send(String topic, UUID orderId, String payload) throws Exception {
        kafkaTemplate.send(topic, orderId.toString(), payload).get(10, TimeUnit.SECONDS);
    }

    private static String orderCreatedJson(UUID orderId, UUID productId, int quantity) {
        return """
                {"eventType":"OrderCreated","orderId":"%s","customerId":"%s",\
                "items":[{"productId":"%s","quantity":%d,"unitPrice":10.00}],\
                "occurredAt":"2026-01-01T00:00:00Z"}""".formatted(orderId, UUID.randomUUID(), productId, quantity);
    }

    /** Reads the inventory-events topic until a message for this order shows up. */
    private static String awaitInventoryEvent(UUID orderId) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of("inventory-events"));
            long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (System.nanoTime() < deadline) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (orderId.toString().equals(record.key())) {
                        return record.value();
                    }
                }
            }
        }
        throw new AssertionError("No inventory event received for order " + orderId);
    }
}
