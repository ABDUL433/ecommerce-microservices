package org.example.messaging;

import org.example.entity.Outbox;
import org.example.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Outbox pattern, part 2: sends rows written in the same transaction as the order
 * to Kafka. A crash between "send" and "mark published" re-sends the event (at-least-once),
 * so consumers must be idempotent.
 *
 * Throughput: the whole batch is sent without waiting for each acknowledgement one by one,
 * and all confirmed rows are marked published with a single UPDATE. Events with the same key
 * go to the same partition, and the idempotent producer keeps their order.
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

    @Scheduled(fixedDelay = 100)
    public void publishPending() {
        List<Outbox> pending = outboxRepository.findTop200ByPublishedFalseOrderByCreatedAtAsc();
        if (pending.isEmpty()) {
            return;
        }

        List<UUID> confirmed = new ArrayList<>(pending.size());
        try {
            List<CompletableFuture<SendResult<String, String>>> sends = new ArrayList<>(pending.size());
            for (Outbox event : pending) {
                sends.add(kafkaTemplate.send(TOPIC, event.getAggregateId().toString(), event.getPayload()));
            }
            for (int i = 0; i < sends.size(); i++) {
                sends.get(i).get(10, TimeUnit.SECONDS);
                confirmed.add(pending.get(i).getId());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // keep order: rows that were not confirmed stay unpublished and are retried on the next tick
            log.warn("Could not publish the whole outbox batch, will retry the rest: {}", e.getMessage());
        }

        if (!confirmed.isEmpty()) {
            outboxRepository.markPublished(confirmed);
            log.debug("Published {} outbox event(s) to {}", confirmed.size(), TOPIC);
        }
    }
}
