package de.dukkan.shared.events;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes events into {@code outbox_event} in the caller's transaction (transactional outbox): an
 * event exists exactly when the change that caused it was committed. {@link OutboxPublisher}
 * sends it afterwards.
 */
@Component
public class EventOutbox {

  private final JdbcClient jdbc;
  private final JsonMapper json;
  private final Clock clock;

  public EventOutbox(JdbcClient jdbc, JsonMapper json, Clock clock) {
    this.jdbc = jdbc;
    this.json = json;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public UUID append(String type, UUID aggregateId, Object payload) {
    UUID id = UUID.randomUUID();
    Instant now = clock.instant();
    jdbc.sql(
            "INSERT INTO outbox_event (id, type, aggregate_id, payload, created_at, next_attempt_at)"
                + " VALUES (:id, :type, :aggregate, CAST(:payload AS jsonb), :now, :now)")
        .param("id", id)
        .param("type", type)
        .param("aggregate", aggregateId)
        .param("payload", json.writeValueAsString(payload))
        .param("now", Timestamp.from(now))
        .update();
    return id;
  }
}
