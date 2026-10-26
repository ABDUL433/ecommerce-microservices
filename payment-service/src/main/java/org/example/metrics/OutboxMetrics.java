package org.example.metrics;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.example.repository.OutboxRepository;
import org.springframework.stereotype.Component;

/** Exposes how many outbox rows are still waiting to be published to Kafka (a growing number means a backlog). */
@Component
public class OutboxMetrics {

    public OutboxMetrics(MeterRegistry registry, OutboxRepository outboxRepository) {
        Gauge.builder("outbox.pending", outboxRepository, OutboxRepository::countByPublishedFalse)
                .description("Outbox rows not yet published to Kafka")
                .register(registry);
    }
}

