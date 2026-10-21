package de.dukkan.shared.events;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends pending outbox events through the configured {@link EventTransport}, oldest first, in
 * batches locked with {@code FOR UPDATE SKIP LOCKED} (several instances never send the same
 * event at once). Failures are retried with exponential backoff (2 s … 10 min); delivery is
 * at-least-once, consumers deduplicate by event id.
 */
@Component
public class OutboxPublisher {

  private static final Logger LOG = LoggerFactory.getLogger(OutboxPublisher.class);
  private static final int BATCH = 50;
  private static final Duration MAX_BACKOFF = Duration.ofMinutes(10);

  private record Pending(OutboxEvent event, int attempts) {}

  private final JdbcClient jdbc;
  private final EventTransport transport;
  private final TransactionTemplate transactions;
  private final Clock clock;

  public OutboxPublisher(JdbcClient jdbc, EventTransport transport, TransactionTemplate transactions, Clock clock) {
    this.jdbc = jdbc;
    this.transport = transport;
    this.transactions = transactions;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "${dukkan.events.publish-interval:PT1S}")
  public void publishScheduled() {
    publishPending();
  }

  /** Sends what is due now; returns the number of events sent. Also called directly by tests. */
  public int publishPending() {
    Integer sent = transactions.execute(status -> publishBatch());
    return sent == null ? 0 : sent;
  }

  private int publishBatch() {
    Instant now = clock.instant();
    List<Pending> batch =
        jdbc.sql(
                "SELECT id, type, aggregate_id, payload::text, created_at, attempts FROM outbox_event"
                    + " WHERE published_at IS NULL AND next_attempt_at <= :now ORDER BY seq LIMIT :limit FOR UPDATE SKIP LOCKED")
            .param("now", Timestamp.from(now))
            .param("limit", BATCH)
            .query(
                (rs, i) ->
                    new Pending(
                        new OutboxEvent(
                            rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class), rs.getString(4), rs.getTimestamp(5).toInstant()),
                        rs.getInt(6)))
            .list();
    int sent = 0;
    for (Pending pending : batch) {
      try {
        transport.send(pending.event());
        jdbc.sql("UPDATE outbox_event SET published_at = :now, attempts = attempts + 1, last_error = NULL WHERE id = :id")
            .param("now", Timestamp.from(clock.instant()))
            .param("id", pending.event().id())
            .update();
        sent++;
      } catch (RuntimeException e) {
        int attempts = pending.attempts() + 1;
        Duration backoff = Duration.ofSeconds(1L << Math.min(attempts, 20));
        Instant next = clock.instant().plus(backoff.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : backoff);
        String error = e.toString();
        jdbc.sql("UPDATE outbox_event SET attempts = :attempts, next_attempt_at = :next, last_error = :error WHERE id = :id")
            .param("attempts", attempts)
            .param("next", Timestamp.from(next))
            .param("error", error.length() > 500 ? error.substring(0, 500) : error)
            .param("id", pending.event().id())
            .update();
        LOG.warn("Event {} ({}) not delivered (attempt {}), retry at {}", pending.event().id(), pending.event().type(), attempts, next);
      }
    }
    return sent;
  }
}
