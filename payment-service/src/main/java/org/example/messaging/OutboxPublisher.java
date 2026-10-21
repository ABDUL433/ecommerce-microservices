package org.example.messaging;

import org.example.entity.Outbox;
import org.example.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Outbox pattern, part 2: sends rows written in the same transaction as the payment
 * to Kafka. A crash between "send" and "mark published" re-sends the event (at-least-once),
 * so consumers must be idempotent.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final String TOPIC = "payment-events";

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(OutboxRepository outboxRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 1000)
    public void publishPending() {
        List<Outbox> pending = outboxRepository.findTop50ByPublishedFalseOrderByCreatedAtAsc();
        for (Outbox event : pending) {
            try {
                kafkaTemplate.send(TOPIC, event.getAggregateId().toString(), event.getPayload())
                        .get(10, TimeUnit.SECONDS);
                outboxRepository.markPublished(event.getId());
                log.info("Published {} for order {}", event.getEventType(), event.getAggregateId());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                // keep order: stop here and retry on the next tick
                log.warn("Could not publish outbox event {} yet, will retry: {}", event.getId(), e.getMessage());
                return;
            }
        }
    }
}
