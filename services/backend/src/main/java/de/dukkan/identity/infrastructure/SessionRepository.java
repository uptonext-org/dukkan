package de.dukkan.identity.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Refresh tokens, password reset tokens and linked social identities (tables from V7). */
@Repository
public class SessionRepository {

  /** A refresh token row; {@code revokedAt} is set once it was rotated, used for logout or revoked. */
  public record RefreshRow(UUID id, UUID userId, UUID familyId, Instant expiresAt, @Nullable Instant revokedAt) {}

  /** A password reset token row. */
  public record ResetRow(UUID id, UUID userId, Instant expiresAt, @Nullable Instant usedAt) {}

  private final JdbcClient jdbc;

  public SessionRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  // ------------------------------------------------------------------ refresh tokens

  public void insertRefresh(UUID id, UUID userId, UUID familyId, byte[] hash, Instant now, Instant expiresAt) {
    jdbc.sql(
            "INSERT INTO refresh_token (id, user_id, family_id, token_hash, created_at, expires_at)"
                + " VALUES (:id, :user, :family, :hash, :now, :expires)")
        .param("id", id)
        .param("user", userId)
        .param("family", familyId)
        .param("hash", hash)
        .param("now", Timestamp.from(now))
        .param("expires", Timestamp.from(expiresAt))
        .update();
  }

  /** Locks the row so that two concurrent refreshes with the same token cannot both rotate it. */
  public Optional<RefreshRow> findRefreshForUpdate(byte[] hash) {
    return jdbc.sql("SELECT id, user_id, family_id, expires_at, revoked_at FROM refresh_token WHERE token_hash = :hash FOR UPDATE")
        .param("hash", hash)
        .query(
            (rs, i) -> {
              Timestamp revoked = rs.getTimestamp(5);
              return new RefreshRow(
                  rs.getObject(1, UUID.class),
                  rs.getObject(2, UUID.class),
                  rs.getObject(3, UUID.class),
                  rs.getTimestamp(4).toInstant(),
                  revoked == null ? null : revoked.toInstant());
            })
        .optional();
  }

  public void markRotated(UUID id, UUID replacedBy, Instant now) {
    jdbc.sql("UPDATE refresh_token SET revoked_at = :now, replaced_by = :next WHERE id = :id")
        .param("now", Timestamp.from(now))
        .param("next", replacedBy)
        .param("id", id)
        .update();
  }

  public int revokeFamily(UUID familyId, Instant now) {
    return jdbc.sql("UPDATE refresh_token SET revoked_at = :now WHERE family_id = :family AND revoked_at IS NULL")
        .param("now", Timestamp.from(now))
        .param("family", familyId)
        .update();
  }

  public int revokeAllForUser(UUID userId, Instant now) {
    return jdbc.sql("UPDATE refresh_token SET revoked_at = :now WHERE user_id = :user AND revoked_at IS NULL")
        .param("now", Timestamp.from(now))
        .param("user", userId)
        .update();
  }

  /** Housekeeping: rotated or expired rows are only needed until they expire (reuse detection). */
  public int deleteExpired(Instant before) {
    int refresh = jdbc.sql("DELETE FROM refresh_token WHERE expires_at < :before").param("before", Timestamp.from(before)).update();
    int reset = jdbc.sql("DELETE FROM password_reset_token WHERE expires_at < :before").param("before", Timestamp.from(before)).update();
    return refresh + reset;
  }

  // ------------------------------------------------------------------ password reset

  public void insertReset(UUID id, UUID userId, byte[] hash, Instant now, Instant expiresAt) {
    jdbc.sql(
            "INSERT INTO password_reset_token (id, user_id, token_hash, created_at, expires_at)"
                + " VALUES (:id, :user, :hash, :now, :expires)")
        .param("id", id)
        .param("user", userId)
        .param("hash", hash)
        .param("now", Timestamp.from(now))
        .param("expires", Timestamp.from(expiresAt))
        .update();
  }

  public Optional<ResetRow> findResetForUpdate(byte[] hash) {
    return jdbc.sql("SELECT id, user_id, expires_at, used_at FROM password_reset_token WHERE token_hash = :hash FOR UPDATE")
        .param("hash", hash)
        .query(
            (rs, i) -> {
              Timestamp used = rs.getTimestamp(4);
              return new ResetRow(
                  rs.getObject(1, UUID.class),
                  rs.getObject(2, UUID.class),
                  rs.getTimestamp(3).toInstant(),
                  used == null ? null : used.toInstant());
            })
        .optional();
  }

  /** Marks every open reset token of the user as used (after a successful reset or password change). */
  public void useAllResets(UUID userId, Instant now) {
    jdbc.sql("UPDATE password_reset_token SET used_at = :now WHERE user_id = :user AND used_at IS NULL")
        .param("now", Timestamp.from(now))
        .param("user", userId)
        .update();
  }

  // ------------------------------------------------------------------ social identities

  public Optional<UUID> findIdentity(String provider, String subject) {
    return jdbc.sql("SELECT user_id FROM user_identity WHERE provider = :provider AND subject = :subject")
        .param("provider", provider)
        .param("subject", subject)
        .query(UUID.class)
        .optional();
  }

  public void insertIdentity(String provider, String subject, UUID userId, @Nullable String email, Instant now) {
    jdbc.sql(
            "INSERT INTO user_identity (provider, subject, user_id, email, created_at) VALUES (:provider, :subject, :user, :email, :now)"
                + " ON CONFLICT (provider, subject) DO NOTHING")
        .param("provider", provider)
        .param("subject", subject)
        .param("user", userId)
        .param("email", email)
        .param("now", Timestamp.from(now))
        .update();
  }
}
