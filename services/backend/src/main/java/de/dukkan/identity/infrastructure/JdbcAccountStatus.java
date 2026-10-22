package de.dukkan.identity.infrastructure;

import de.dukkan.shared.security.AccountStatus;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** One primary-key lookup per authenticated request; deleted accounts keep their row (anonymised). */
@Component
class JdbcAccountStatus implements AccountStatus {

  private final JdbcClient jdbc;

  JdbcAccountStatus(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean isClosed(UUID userId) {
    return jdbc.sql("SELECT deleted_at IS NULL FROM app_user WHERE id = :id")
        .param("id", userId)
        .query(Boolean.class)
        .optional()
        .map(active -> !active)
        .orElse(true);
  }
}
