package de.dukkan.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** PostgreSQL 18.6 via Testcontainers, a controllable clock and mailers that capture reset links and order e-mails. */
@TestConfiguration(proxyBeanMethods = false)
public class TestInfrastructure {

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgres() {
    return new PostgreSQLContainer("postgres:18.6");
  }

  @Bean
  @Primary
  MutableClock testClock() {
    return new MutableClock();
  }

  @Bean
  @Primary
  CapturingAccountMailer capturingAccountMailer() {
    return new CapturingAccountMailer();
  }

  @Bean
  @Primary
  CapturingMailer capturingMailer() {
    return new CapturingMailer();
  }
}
