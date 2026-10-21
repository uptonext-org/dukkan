package de.dukkan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.shared.events.EventDispatcher;
import de.dukkan.shared.events.OutboxEvent;
import de.dukkan.shared.events.OutboxPublisher;
import de.dukkan.support.IntegrationTest;
import de.dukkan.support.OpenApiContract;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/** B-37: order events from the outbox become inbox notifications in each recipient's language. */
class NotificationIntegrationTest extends IntegrationTest {

  private static final OpenApiContract CONTRACT = OpenApiContract.load();

  @Autowired private OutboxPublisher publisher;
  @Autowired private EventDispatcher dispatcher;

  /** Publishes pending events and waits until the user has {@code count} notifications. */
  MvcResult awaitInbox(String token, int count) throws Exception {
    for (int i = 0; i < 50; i++) {
      publisher.publishPending();
      MvcResult result = mvc.perform(auth(get("/api/v1/me/notifications"), token)).andExpect(status().isOk()).andReturn();
      if (((Integer) read(result, "$.totalItems")) >= count) {
        return result;
      }
      Thread.sleep(100);
    }
    throw new AssertionError("Expected " + count + " notification(s)");
  }

  String customerOrder(String customer, UUID market, UUID listing) throws Exception {
    return orderPickup(customer, market, listing, 1);
  }

  @Test
  void merchantsHearAboutNewAndCancelledOrdersCustomersAboutDecisions() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Benachrichtigungsmarkt");
    UUID listing = addListing(merchant, market, "Ayran", 650);
    String customer = registerUser();
    mvc.perform(json(auth(patch("/api/v1/me"), customer), toJson(Map.of("preferredLocale", "en")))).andExpect(status().isOk());

    String order = customerOrder(customer, market, listing);
    MvcResult merchantInbox = awaitInbox(merchant, 1);
    assertThat((String) read(merchantInbox, "$.items[0].kind")).isEqualTo("NEW_ORDER");
    assertThat((String) read(merchantInbox, "$.items[0].title")).isEqualTo("Neue Bestellung");
    assertThat((String) read(merchantInbox, "$.items[0].body")).matches("DK-\\d{8}-\\d{6} · 6,50 € · Abholung");
    assertThat((String) read(merchantInbox, "$.items[0].orderId")).isEqualTo(order);
    assertThat((Integer) read(merchantInbox, "$.unreadCount")).isEqualTo(1);
    assertThat(CONTRACT.requiredFields("NotificationPage")).allSatisfy(f -> assertThat(object(merchantInbox, "$." + f)).as(f).isNotNull());
    assertThat(CONTRACT.requiredFields("Notification")).allSatisfy(f -> assertThat(object(merchantInbox, "$.items[0]." + f)).as(f).isNotNull());

    mvc.perform(json(auth(post("/api/v1/merchant/orders/" + order + "/status"), merchant), toJson(Map.of("status", "ACCEPTED"))))
        .andExpect(status().isOk());
    MvcResult customerInbox = awaitInbox(customer, 1);
    assertThat((String) read(customerInbox, "$.items[0].kind")).isEqualTo("ORDER_ACCEPTED");
    assertThat((String) read(customerInbox, "$.items[0].title")).isEqualTo("Order accepted");
    assertThat((String) read(customerInbox, "$.items[0].body")).startsWith("Benachrichtigungsmarkt · DK-");

    mvc.perform(auth(post("/api/v1/orders/" + order + "/cancel"), customer)).andExpect(status().isOk());
    MvcResult cancelled = awaitInbox(merchant, 2);
    assertThat((String) read(cancelled, "$.items[0].kind")).isEqualTo("ORDER_CANCELLED");
    assertThat((Integer) read(cancelled, "$.unreadCount")).isEqualTo(2);
    // The customer cancelled it: no notification to the customer about their own action.
    assertThat((Integer) read(awaitInbox(customer, 1), "$.totalItems")).isEqualTo(1);

    mvc.perform(auth(post("/api/v1/me/notifications/read"), merchant)).andExpect(status().isNoContent());
    mvc.perform(auth(get("/api/v1/me/notifications"), merchant))
        .andExpect(jsonPath("$.unreadCount").value(0))
        .andExpect(jsonPath("$.items[0].read").value(true));
    // Inboxes are private.
    mvc.perform(get("/api/v1/me/notifications")).andExpect(status().isUnauthorized());
  }

  @Test
  void eventsAreHandledOnceEvenWhenDeliveredTwice() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Doppelmarkt");
    UUID listing = addListing(merchant, market, "Ayran", 700);
    String order = customerOrder(registerUser(), market, listing);
    awaitInbox(merchant, 1);

    Map<String, Object> row =
        jdbc.sql("SELECT id, type, aggregate_id, payload::text AS payload FROM outbox_event WHERE type = 'order.placed' AND aggregate_id = :order")
            .param("order", UUID.fromString(order))
            .query()
            .singleRow();
    OutboxEvent again =
        new OutboxEvent((UUID) row.get("id"), (String) row.get("type"), (UUID) row.get("aggregate_id"), (String) row.get("payload"), clock.instant());
    dispatcher.dispatch(again);
    dispatcher.dispatch(again);
    assertThat((Integer) read(awaitInbox(merchant, 1), "$.totalItems")).isEqualTo(1);
    assertThat(jdbc.sql("SELECT published_at IS NOT NULL FROM outbox_event WHERE id = :id").param("id", again.id()).query(Boolean.class).single())
        .isTrue();
  }

  @Test
  void devicesAreRegisteredPerUserAndCanBeRemoved() throws Exception {
    String first = registerUser();
    String second = registerUser();
    String token = "fcm-" + UUID.randomUUID();
    mvc.perform(json(auth(put("/api/v1/me/devices"), first), toJson(Map.of("token", token, "platform", "ANDROID")))).andExpect(status().isNoContent());
    assertThat(owner(token)).isEqualTo(userId(first));
    // The same phone signed in with another account now belongs to that account.
    mvc.perform(json(auth(put("/api/v1/me/devices"), second), toJson(Map.of("token", token, "platform", "IOS")))).andExpect(status().isNoContent());
    assertThat(owner(token)).isEqualTo(userId(second));
    mvc.perform(json(auth(delete("/api/v1/me/devices"), first), toJson(Map.of("token", token)))).andExpect(status().isNoContent());
    assertThat(owner(token)).isEqualTo(userId(second));
    mvc.perform(json(auth(delete("/api/v1/me/devices"), second), toJson(Map.of("token", token)))).andExpect(status().isNoContent());
    assertThat(jdbc.sql("SELECT count(*) FROM device_token WHERE token = :t").param("t", token).query(Long.class).single()).isZero();
    mvc.perform(json(auth(put("/api/v1/me/devices"), first), toJson(Map.of("token", token, "platform", "WINDOWS")))).andExpect(status().isBadRequest());
  }

  private UUID owner(String token) {
    List<UUID> users = jdbc.sql("SELECT user_id FROM device_token WHERE token = :t").param("t", token).query(UUID.class).list();
    return users.getFirst();
  }
}
