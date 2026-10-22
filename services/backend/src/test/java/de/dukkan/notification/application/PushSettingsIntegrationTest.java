package de.dukkan.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.shared.events.OutboxPublisher;
import de.dukkan.support.IntegrationTest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** B-37a: users switch push off (the inbox still fills); old read notifications are cleaned up. */
class PushSettingsIntegrationTest extends IntegrationTest {

  /** Records pushes instead of calling Firebase. */
  static final class RecordingPushGateway implements PushGateway {
    final List<String> tokens = new CopyOnWriteArrayList<>();

    @Override
    public boolean enabled() {
      return true;
    }

    @Override
    public List<String> send(List<String> deviceTokens, PushMessage message) {
      tokens.addAll(deviceTokens);
      return List.of();
    }
  }

  @TestConfiguration
  static class PushConfiguration {
    @Bean
    @Primary
    RecordingPushGateway recordingPushGateway() {
      return new RecordingPushGateway();
    }
  }

  @Autowired private RecordingPushGateway pushes;
  @Autowired private OutboxPublisher publisher;
  @Autowired private NotificationRetentionJob retention;

  private long inboxSize(String token) throws Exception {
    return ((Number) read(mvc.perform(auth(get("/api/v1/me/notifications"), token)).andReturn(), "$.totalItems")).longValue();
  }

  private void awaitInbox(String token, long count) throws Exception {
    for (int i = 0; i < 50 && inboxSize(token) < count; i++) {
      publisher.publishPending();
      Thread.sleep(100);
    }
    assertThat(inboxSize(token)).isGreaterThanOrEqualTo(count);
  }

  @Test
  void pushFollowsTheUsersSwitchWhileTheInboxAlwaysFills() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Pushmarkt");
    UUID listing = addListing(merchant, market, "Ayran", 650);
    String device = "fcm-" + UUID.randomUUID();
    mvc.perform(json(auth(put("/api/v1/me/devices"), merchant), toJson(Map.of("token", device, "platform", "ANDROID")))).andExpect(status().isNoContent());

    mvc.perform(auth(get("/api/v1/me/notification-settings"), merchant)).andExpect(status().isOk()).andExpect(jsonPath("$.pushEnabled").value(true));
    orderPickup(registerUser(), market, listing, 1);
    awaitInbox(merchant, 1);
    assertThat(pushes.tokens).contains(device);

    mvc.perform(json(auth(put("/api/v1/me/notification-settings"), merchant), "{\"pushEnabled\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pushEnabled").value(false));
    pushes.tokens.clear();
    orderPickup(registerUser(), market, listing, 1);
    awaitInbox(merchant, 2);
    assertThat(pushes.tokens).doesNotContain(device);

    mvc.perform(json(auth(put("/api/v1/me/notification-settings"), merchant), "{}")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/me/notification-settings")).andExpect(status().isUnauthorized());
  }

  @Test
  void readNotificationsOlderThanTheRetentionAreDeleted() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Aufräummarkt");
    UUID listing = addListing(merchant, market, "Ayran", 650);
    orderPickup(registerUser(), market, listing, 1);
    awaitInbox(merchant, 1);
    orderPickup(registerUser(), market, listing, 1);
    awaitInbox(merchant, 2);
    // Read one of two; after 181 days only the unread one stays.
    UUID user = userId(merchant);
    jdbc.sql("UPDATE notification SET read_at = created_at WHERE id = (SELECT id FROM notification WHERE user_id = :u ORDER BY seq LIMIT 1)")
        .param("u", user)
        .update();
    clock.advance(Duration.ofDays(179));
    retention.deleteOld();
    String count = "SELECT count(*) FROM notification WHERE user_id = :u";
    assertThat(jdbc.sql(count).param("u", user).query(Long.class).single()).isEqualTo(2);
    clock.advance(Duration.ofDays(2));
    retention.deleteOld();
    assertThat(jdbc.sql(count + " AND read_at IS NULL").param("u", user).query(Long.class).single()).isEqualTo(1);
    assertThat(jdbc.sql(count).param("u", user).query(Long.class).single()).isEqualTo(1);
  }
}
