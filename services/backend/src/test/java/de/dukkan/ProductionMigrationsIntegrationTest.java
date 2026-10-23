package de.dukkan;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import de.dukkan.support.IntegrationTest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** The prod profile migrates db/migration only: schema and reference data, none of the invented Hamburg data. */
class ProductionMigrationsIntegrationTest extends IntegrationTest {

  @Autowired HikariDataSource dataSource;

  @Test
  void aProductionDatabaseStartsWithReferenceDataButWithoutDemoMarkets() throws SQLException {
    jdbc.sql("DROP DATABASE IF EXISTS prod_check").update();
    jdbc.sql("CREATE DATABASE prod_check").update();
    String url = dataSource.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/prod_check$1");

    Flyway.configure()
        .dataSource(url, dataSource.getUsername(), dataSource.getPassword())
        .locations("classpath:db/migration")
        .load()
        .migrate();

    try (Connection connection = DriverManager.getConnection(url, dataSource.getUsername(), dataSource.getPassword());
        Statement statement = connection.createStatement()) {
      assertThat(count(statement, "market")).isZero();
      assertThat(count(statement, "catalog_product")).isZero();
      assertThat(count(statement, "promotion")).isZero();
      assertThat(count(statement, "app_user")).isZero();
      assertThat(count(statement, "market_type")).isPositive();
      assertThat(count(statement, "category")).isPositive();
      assertThat(count(statement, "supported_locale")).isEqualTo(3);
    } finally {
      jdbc.sql("DROP DATABASE IF EXISTS prod_check WITH (FORCE)").update();
    }
  }

  private static long count(Statement statement, String table) throws SQLException {
    try (ResultSet rows = statement.executeQuery("SELECT count(*) FROM " + table)) {
      rows.next();
      return rows.getLong(1);
    }
  }
}
