package de.dukkan.shared.events;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Outbox health for alerting (read on each scrape, two indexed queries): {@code
 * dukkan.outbox.pending} events not yet published, {@code dukkan.outbox.retrying} of those that
 * already failed at least once, and {@code dukkan.outbox.oldest.pending.seconds}, the age of the
 * oldest one. A growing age means the publisher or the broker is stuck.
 */
@Component
class OutboxMetrics {

  OutboxMetrics(MeterRegistry registry, JdbcClient jdbc, Clock clock) {
    Gauge.builder(
            "dukkan.outbox.pending", jdbc, j -> j.sql("SELECT count(*) FROM outbox_event WHERE published_at IS NULL").query(Long.class).single())
        .description("Outbox events not yet published")
        .register(registry);
    Gauge.builder(
            "dukkan.outbox.retrying",
            jdbc,
            j -> j.sql("SELECT count(*) FROM outbox_event WHERE published_at IS NULL AND attempts > 0").query(Long.class).single())
        .description("Unpublished outbox events that failed at least once")
        .register(registry);
    Gauge.builder(
            "dukkan.outbox.oldest.pending.seconds",
            jdbc,
            j ->
                j.sql("SELECT min(created_at) FROM outbox_event WHERE published_at IS NULL")
                    .query(Timestamp.class)
                    .optional()
                    .map(t -> (double) Duration.between(t.toInstant(), clock.instant()).toSeconds())
                    .orElse(0.0))
        .description("Age of the oldest unpublished outbox event")
        .baseUnit("seconds")
        .register(registry);
  }
}
