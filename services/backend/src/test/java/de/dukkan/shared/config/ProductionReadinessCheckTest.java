package de.dukkan.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.dukkan.support.DukkanPropertiesFixture;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionReadinessCheckTest {

  private static DukkanProperties withLinks(DukkanProperties base, String resetUrl, String verifyUrl) {
    DukkanProperties.Security security = base.security();
    DukkanProperties.Auth auth = security.auth();
    return new DukkanProperties(
        base.features(),
        base.fulfilment(),
        base.catalog(),
        base.markets(),
        new DukkanProperties.Security(
            security.jwt(),
            security.rateLimit(),
            new DukkanProperties.Auth(
                auth.refreshTokenTtl(),
                auth.passwordResetTtl(),
                resetUrl,
                auth.mailFrom(),
                false,
                auth.google(),
                auth.apple(),
                verifyUrl,
                Duration.ofDays(3),
                false)),
        base.media(),
        base.capture(),
        base.push(),
        base.ai(),
        base.translation(),
        base.shoppingList());
  }

  private static MockEnvironment ready() {
    MockEnvironment environment = new MockEnvironment()
        .withProperty("spring.datasource.password", "s3cret-from-the-vault")
        .withProperty("spring.mail.host", "smtp.example.org");
    environment.setActiveProfiles("prod");
    return environment;
  }

  @Test
  void acceptsAProductionConfiguration() {
    DukkanProperties properties =
        withLinks(DukkanPropertiesFixture.defaults(), "https://dukkan.example/reset-password", "https://dukkan.example/verify-email");
    assertThat(ProductionReadinessCheck.problems(ready(), properties)).isEmpty();
  }

  @Test
  void listsEveryDevelopmentSettingAtOnce() {
    MockEnvironment environment = new MockEnvironment().withProperty("spring.datasource.password", "dukkan");
    environment.setActiveProfiles("prod", "demo");
    DukkanProperties properties = DukkanPropertiesFixture.withJwt("local-dev-secret-change-me-0123456789abcdef", Duration.ofMinutes(15));

    List<String> problems = ProductionReadinessCheck.problems(environment, properties);

    assertThat(problems)
        .hasSize(6)
        .anySatisfy(p -> assertThat(p).contains("profile 'demo'"))
        .anySatisfy(p -> assertThat(p).contains("DUKKAN_DB_PASSWORD"))
        .anySatisfy(p -> assertThat(p).contains("DUKKAN_JWT_SECRET"))
        .anySatisfy(p -> assertThat(p).contains("SPRING_MAIL_HOST"))
        .anySatisfy(p -> assertThat(p).contains("DUKKAN_PASSWORD_RESET_URL").contains("dukkan://reset-password"))
        .anySatisfy(p -> assertThat(p).contains("DUKKAN_EMAIL_VERIFICATION_URL"));
    assertThatThrownBy(() -> new ProductionReadinessCheck(environment, properties))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Production profile refused to start");
  }

  @Test
  void rejectsTheDefaultBrokerCredentials() {
    MockEnvironment environment = ready()
        .withProperty("dukkan.events.transport", "rabbitmq")
        .withProperty("dukkan.events.rabbitmq-uri", "amqp://dukkan:dukkan@localhost:5672");
    DukkanProperties properties =
        withLinks(DukkanPropertiesFixture.defaults(), "https://dukkan.example/reset-password", "https://dukkan.example/verify-email");
    assertThat(ProductionReadinessCheck.problems(environment, properties)).singleElement().asString().contains("DUKKAN_RABBITMQ_URI");
  }
}
