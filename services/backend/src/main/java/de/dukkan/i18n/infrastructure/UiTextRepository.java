package de.dukkan.i18n.infrastructure;

import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class UiTextRepository {

  private final JdbcClient jdbc;

  public UiTextRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** key → locale → text. */
  public Map<String, Map<String, String>> findAll() {
    Map<String, Map<String, String>> texts = new HashMap<>();
    jdbc.sql("SELECT key, locale, text FROM ui_text")
        .query(
            rs -> {
              texts.computeIfAbsent(rs.getString("key"), k -> new HashMap<>()).put(rs.getString("locale"), rs.getString("text"));
            });
    return texts;
  }
}
