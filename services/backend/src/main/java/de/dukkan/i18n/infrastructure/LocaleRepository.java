package de.dukkan.i18n.infrastructure;

import de.dukkan.i18n.application.SupportedLocale;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class LocaleRepository {

  private final JdbcClient jdbc;

  public LocaleRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public List<SupportedLocale> findEnabled() {
    return jdbc.sql(
            "SELECT code, native_name, direction FROM supported_locale WHERE enabled ORDER BY sort_order, code")
        .query((rs, i) -> new SupportedLocale(rs.getString("code"), rs.getString("native_name"), rs.getString("direction")))
        .list();
  }
}
