package de.dukkan.notification.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Device tokens and the notification inbox (tables from V8). */
@Repository
public class NotificationRepository {

  /** One inbox entry. */
  public record NotificationRow(
      UUID id, String kind, String title, String body, @Nullable UUID orderId, @Nullable UUID marketId, Instant createdAt, @Nullable Instant readAt) {}

  private final JdbcClient jdbc;

  public NotificationRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** A token belongs to the user who registered it last (devices change hands on sign-in). */
  public void upsertDevice(String token, UUID userId, String platform, Instant now) {
    jdbc.sql(
            "INSERT INTO device_token (token, user_id, platform, created_at, last_seen_at) VALUES (:token, :user, :platform, :now, :now)"
                + " ON CONFLICT (token) DO UPDATE SET user_id = EXCLUDED.user_id, platform = EXCLUDED.platform, last_seen_at = EXCLUDED.last_seen_at")
        .param("token", token)
        .param("user", userId)
        .param("platform", platform)
        .param("now", Timestamp.from(now))
        .update();
  }

  public void deleteDevice(String token, UUID userId) {
    jdbc.sql("DELETE FROM device_token WHERE token = :token AND user_id = :user").param("token", token).param("user", userId).update();
  }

  public void deleteDevices(Collection<String> tokens) {
    if (!tokens.isEmpty()) {
      jdbc.sql("DELETE FROM device_token WHERE token IN (:tokens)").param("tokens", tokens).update();
    }
  }

  public List<String> deviceTokens(UUID userId) {
    return jdbc.sql("SELECT token FROM device_token WHERE user_id = :user ORDER BY last_seen_at DESC LIMIT 10").param("user", userId).query(String.class).list();
  }

  public void insert(UUID id, UUID userId, String kind, String title, String body, @Nullable UUID orderId, @Nullable UUID marketId, Instant now) {
    jdbc.sql(
            "INSERT INTO notification (id, user_id, kind, title, body, order_id, market_id, created_at)"
                + " VALUES (:id, :user, :kind, :title, :body, :order, :market, :now)")
        .param("id", id)
        .param("user", userId)
        .param("kind", kind)
        .param("title", title)
        .param("body", body)
        .param("order", orderId)
        .param("market", marketId)
        .param("now", Timestamp.from(now))
        .update();
  }

  public List<NotificationRow> page(UUID userId, int page, int size) {
    return jdbc.sql(
            "SELECT id, kind, title, body, order_id, market_id, created_at, read_at FROM notification WHERE user_id = :user"
                + " ORDER BY seq DESC LIMIT :limit OFFSET :offset")
        .param("user", userId)
        .param("limit", size)
        .param("offset", (long) page * size)
        .query(
            (rs, i) -> {
              Timestamp read = rs.getTimestamp(8);
              return new NotificationRow(
                  rs.getObject(1, UUID.class),
                  rs.getString(2),
                  rs.getString(3),
                  rs.getString(4),
                  rs.getObject(5, UUID.class),
                  rs.getObject(6, UUID.class),
                  rs.getTimestamp(7).toInstant(),
                  read == null ? null : read.toInstant());
            })
        .list();
  }

  public long count(UUID userId) {
    return jdbc.sql("SELECT count(*) FROM notification WHERE user_id = :user").param("user", userId).query(Long.class).single();
  }

  public long unreadCount(UUID userId) {
    return jdbc.sql("SELECT count(*) FROM notification WHERE user_id = :user AND read_at IS NULL").param("user", userId).query(Long.class).single();
  }

  public int markAllRead(UUID userId, Instant now) {
    return jdbc.sql("UPDATE notification SET read_at = :now WHERE user_id = :user AND read_at IS NULL")
        .param("now", Timestamp.from(now))
        .param("user", userId)
        .update();
  }

  /** Push switch of a user; absent means on. */
  public Optional<Boolean> pushEnabled(UUID userId) {
    return jdbc.sql("SELECT push_enabled FROM notification_setting WHERE user_id = :userId").param("userId", userId).query(Boolean.class).optional();
  }

  public void setPushEnabled(UUID userId, boolean enabled, Instant now) {
    jdbc.sql(
            """
            INSERT INTO notification_setting (user_id, push_enabled, updated_at) VALUES (:userId, :enabled, :now)
            ON CONFLICT (user_id) DO UPDATE SET push_enabled = EXCLUDED.push_enabled, updated_at = EXCLUDED.updated_at
            """)
        .param("userId", userId)
        .param("enabled", enabled)
        .param("now", Timestamp.from(now))
        .update();
  }

  /** Read notifications created before the cutoff; unread ones stay. */
  public int deleteReadBefore(Instant cutoff) {
    return jdbc.sql("DELETE FROM notification WHERE read_at IS NOT NULL AND created_at < :cutoff").param("cutoff", Timestamp.from(cutoff)).update();
  }
}
