package de.dukkan.feedback.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class FeedbackRepository {

  /** One stored feedback message. */
  public record FeedbackRow(UUID id, UUID marketId, UUID userId, @Nullable Integer rating, String message, Instant createdAt) {}

  private static final String COLUMNS = "SELECT id, market_id, user_id, rating, message, created_at FROM market_feedback";

  private final JdbcClient jdbc;

  public FeedbackRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public boolean enabled(UUID marketId) {
    return jdbc.sql("SELECT enabled FROM market_feedback_setting WHERE market_id = :marketId")
        .param("marketId", marketId)
        .query(Boolean.class)
        .optional()
        .orElse(false);
  }

  public void setEnabled(UUID marketId, boolean enabled, Instant now) {
    jdbc.sql(
            """
            INSERT INTO market_feedback_setting (market_id, enabled, updated_at) VALUES (:marketId, :enabled, :now)
            ON CONFLICT (market_id) DO UPDATE SET enabled = EXCLUDED.enabled, updated_at = EXCLUDED.updated_at
            """)
        .param("marketId", marketId)
        .param("enabled", enabled)
        .param("now", Timestamp.from(now))
        .update();
  }

  public void insert(UUID id, UUID marketId, UUID userId, @Nullable Integer rating, String message, Instant now) {
    jdbc.sql(
            "INSERT INTO market_feedback (id, market_id, user_id, rating, message, created_at)"
                + " VALUES (:id, :marketId, :userId, :rating, :message, :now)")
        .param("id", id)
        .param("marketId", marketId)
        .param("userId", userId)
        .param("rating", rating, Types.SMALLINT)
        .param("message", message)
        .param("now", Timestamp.from(now))
        .update();
  }

  /** Newest first. */
  public List<FeedbackRow> page(UUID marketId, int page, int size) {
    return jdbc.sql(COLUMNS + " WHERE market_id = :marketId ORDER BY created_at DESC, id LIMIT :limit OFFSET :offset")
        .param("marketId", marketId)
        .param("limit", size)
        .param("offset", (long) page * size)
        .query(FeedbackRepository::row)
        .list();
  }

  public long count(UUID marketId) {
    return jdbc.sql("SELECT count(*) FROM market_feedback WHERE market_id = :marketId").param("marketId", marketId).query(Long.class).single();
  }

  public List<FeedbackRow> byUser(UUID userId) {
    return jdbc.sql(COLUMNS + " WHERE user_id = :userId ORDER BY created_at DESC, id").param("userId", userId).query(FeedbackRepository::row).list();
  }

  public void deleteByUser(UUID userId) {
    jdbc.sql("DELETE FROM market_feedback WHERE user_id = :userId").param("userId", userId).update();
  }

  private static FeedbackRow row(ResultSet rs, int rowNum) throws SQLException {
    return new FeedbackRow(
        rs.getObject("id", UUID.class),
        rs.getObject("market_id", UUID.class),
        rs.getObject("user_id", UUID.class),
        rs.getObject("rating", Integer.class),
        rs.getString("message"),
        rs.getTimestamp("created_at").toInstant());
  }
}
