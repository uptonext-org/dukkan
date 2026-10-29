package de.dukkan.shared.events;

import static org.assertj.core.api.Assertions.assertThat;

import de.dukkan.support.IntegrationTest;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** B-37a: published events and their idempotency records are removed after the retention. */
class OutboxRetentionIntegrationTest extends IntegrationTest {

  @Autowired private OutboxPublisher publisher;
  @Autowired private OutboxRetentionJob retention;

  private long count(String sql, UUID order) {
    return jdbc.sql(sql).param("order", order).query(Long.class).single();
  }

  @Test
  void publishedEventsGoAfterTheRetentionUnpublishedOnesStay() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Outboxmarkt");
    UUID listing = addListing(merchant, market, "Ayran", 650);
    UUID order = UUID.fromString(orderPickup(registerUser(), market, listing, 1));
    publisher.publishPending();
    String events = "SELECT count(*) FROM outbox_event WHERE aggregate_id = :order";
    String processed = "SELECT count(*) FROM processed_event p JOIN outbox_event e ON e.id = p.event_id WHERE e.aggregate_id = :order";
    assertThat(count(events + " AND published_at IS NOT NULL", order)).isEqualTo(1);
    assertThat(count(processed, order)).isPositive();
    // An unpublished event of the same age must survive.
    UUID stuck = UUID.randomUUID();
    jdbc.sql(
            "INSERT INTO outbox_event (id, type, aggregate_id, payload, created_at, next_attempt_at, attempts)"
                + " VALUES (:id, 'test.stuck', :order, '{}'::jsonb, :now, :later, 9)")
        .param("id", stuck)
        .param("order", order)
        .param("now", java.sql.Timestamp.from(clock.instant()))
        .param("later", java.sql.Timestamp.from(clock.instant().plus(Duration.ofDays(400))))
        .update();
    // One of its handlers already succeeded: that marker must stay while the event waits for a retry.
    jdbc.sql("INSERT INTO processed_event (event_id, handler, processed_at) VALUES (:id, 'done-handler', :now)")
        .param("id", stuck)
        .param("now", java.sql.Timestamp.from(clock.instant()))
        .update();

    clock.advance(Duration.ofDays(13));
    retention.deleteOld();
    assertThat(count(events, order)).isEqualTo(2);

    clock.advance(Duration.ofDays(2));
    retention.deleteOld();
    assertThat(count(events, order)).isEqualTo(1);
    assertThat(jdbc.sql("SELECT count(*) FROM processed_event WHERE event_id = :id").param("id", stuck).query(Long.class).single()).isEqualTo(1);
    assertThat(jdbc.sql("SELECT count(*) FROM processed_event p WHERE NOT EXISTS (SELECT 1 FROM outbox_event e WHERE e.id = p.event_id) AND p.processed_at < :cutoff")
            .param("cutoff", java.sql.Timestamp.from(clock.instant().minus(Duration.ofDays(14))))
            .query(Long.class)
            .single())
        .isZero();
  }
}
