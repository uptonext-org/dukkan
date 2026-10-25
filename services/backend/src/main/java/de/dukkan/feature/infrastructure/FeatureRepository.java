package de.dukkan.feature.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** SQL access to {@code platform_feature}. */
@Repository
public class FeatureRepository {

  private final JdbcClient jdbc;

  public FeatureRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Stored switches: key → enabled. */
  public Map<String, Boolean> findAll() {
    Map<String, Boolean> result = new HashMap<>();
    jdbc.sql("SELECT feature_key, enabled FROM platform_feature").query(rs -> {
      result.put(rs.getString(1), rs.getBoolean(2));
    });
    return result;
  }

  public void save(String key, boolean enabled, UUID by, Instant now) {
    jdbc.sql(
            "INSERT INTO platform_feature (feature_key, enabled, updated_at, updated_by) VALUES (:key, :enabled, :now, :by)"
                + " ON CONFLICT (feature_key) DO UPDATE SET enabled = :enabled, updated_at = :now, updated_by = :by")
        .param("key", key)
        .param("enabled", enabled)
        .param("now", Timestamp.from(now))
        .param("by", by)
        .update();
  }
}
