package de.dukkan.shared.events;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nightly removal of published outbox events and of the consumers' idempotency records older
 * than {@code dukkan.events.retention} (default 14 days, 03:40 Europe/Berlin). Unpublished events
 * are never removed. A redelivery of an event older than the retention would run its handlers
 * again, which is why the retention is far longer than any broker redelivery.
 */
@Component
class OutboxRetentionJob {

  private static final Logger LOG = LoggerFactory.getLogger(OutboxRetentionJob.class);

  private final JdbcClient jdbc;
  private final Clock clock;
  private final Duration retention;

  OutboxRetentionJob(JdbcClient jdbc, Clock clock, @Value("${dukkan.events.retention:P14D}") Duration retention) {
    if (retention.compareTo(Duration.ofDays(1)) < 0) {
      throw new IllegalArgumentException("dukkan.events.retention must be at least one day");
    }
    this.jdbc = jdbc;
    this.clock = clock;
    this.retention = retention;
  }

  @Scheduled(cron = "0 40 3 * * *", zone = "Europe/Berlin")
  @Transactional
  public void deleteOld() {
    Timestamp cutoff = Timestamp.from(clock.instant().minus(retention));
    int events = jdbc.sql("DELETE FROM outbox_event WHERE published_at IS NOT NULL AND published_at < :cutoff").param("cutoff", cutoff).update();
    int processed = jdbc.sql("DELETE FROM processed_event WHERE processed_at < :cutoff").param("cutoff", cutoff).update();
    if (events + processed > 0) {
      LOG.info("Deleted {} published event(s) and {} processing record(s) older than {}", events, processed, cutoff);
    }
  }
}
