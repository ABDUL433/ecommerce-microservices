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
 * Outbox pattern, part 2: a poller sends rows written in the same transaction as the
 * order to Kafka. If the app crashes between "send" and "mark published", the event
 * is sent again, so consumers must be idempotent (at-least-once delivery).
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final String TOPIC = "order-events";

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
                log.info("Published {} for aggregate {}", event.getEventType(), event.getAggregateId());
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
