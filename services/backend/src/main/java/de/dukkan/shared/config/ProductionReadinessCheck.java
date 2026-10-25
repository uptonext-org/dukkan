package de.dukkan.shared.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Refuses to start the {@code prod} profile with settings that are fine for development but wrong
 * for real customers. Problems are collected and reported together, so one restart fixes all.
 */
@Component
@Profile("prod")
final class ProductionReadinessCheck {

  private static final Logger LOG = LoggerFactory.getLogger(ProductionReadinessCheck.class);

  /** Values from the repository (.env.example, compose, local profile) that must never reach production. */
  static final Set<String> KNOWN_PLACEHOLDERS =
      Set.of(
          "dukkan",
          "change-me-to-a-long-random-secret-of-at-least-32-bytes",
          "local-dev-secret-change-me-0123456789abcdef");

  ProductionReadinessCheck(Environment environment, DukkanProperties properties) {
    List<String> problems = problems(environment, properties);
    if (!problems.isEmpty()) {
      throw new IllegalStateException(
          "Production profile refused to start:\n - " + String.join("\n - ", problems) + "\nSee docs/launch/go-live.md.");
    }
    if (properties.features().merchantAutoApprove()) {
      LOG.warn("dukkan.features.merchant-auto-approve is on: new markets go live without review");
    }
    if ("filesystem".equals(properties.media().storage())) {
      LOG.warn("Media on the local file system ({}): run one instance and back the directory up", properties.media().directory());
    }
  }

  static List<String> problems(Environment environment, DukkanProperties properties) {
    List<String> problems = new ArrayList<>();
    for (String profile : environment.getActiveProfiles()) {
      if (profile.equals("local") || profile.equals("demo")) {
        problems.add("profile '" + profile + "' creates demo accounts or relaxes limits; remove it");
      }
    }
    String dbPassword = environment.getProperty("spring.datasource.password", "");
    if (dbPassword.isBlank() || KNOWN_PLACEHOLDERS.contains(dbPassword)) {
      problems.add("DUKKAN_DB_PASSWORD is empty or a known default");
    }
    if (KNOWN_PLACEHOLDERS.contains(properties.security().jwt().secret())) {
      problems.add("DUKKAN_JWT_SECRET is a placeholder from the repository; generate one (openssl rand -base64 48)");
    }
    if (environment.getProperty("spring.mail.host", "").isBlank()) {
      problems.add("SPRING_MAIL_HOST is not set: password reset and verification mails would be dropped");
    }
    DukkanProperties.Auth auth = properties.security().auth();
    requireHttps(problems, "DUKKAN_PASSWORD_RESET_URL", auth.passwordResetUrl(), "reset-password");
    requireHttps(problems, "DUKKAN_EMAIL_VERIFICATION_URL", auth.emailVerificationUrl(), "verify-email");
    if (auth.logResetLinks()) {
      problems.add("dukkan.security.auth.log-reset-links writes reset links to the log; turn it off");
    }
    if (!properties.security().rateLimit().enabled()) {
      problems.add("dukkan.security.rate-limit.enabled is false");
    }
    if (environment.getProperty("dukkan.privacy.erasure-journal", "").isBlank()) {
      problems.add("DUKKAN_ERASURE_JOURNAL is empty: a restored backup would bring deleted accounts back");
    }
    if (!"ON_SITE".equals(environment.getProperty("dukkan.platform.payment"))) {
      problems.add("DUKKAN_PAYMENT must be ON_SITE: without online payment customers pay the market, and the apps must say so");
    }
    String rabbit = environment.getProperty("dukkan.events.rabbitmq-uri", "");
    if ("rabbitmq".equals(environment.getProperty("dukkan.events.transport")) && rabbit.contains("dukkan:dukkan@")) {
      problems.add("DUKKAN_RABBITMQ_URI uses the default credentials");
    }
    return problems;
  }

  private static void requireHttps(List<String> problems, String name, String url, String path) {
    if (!url.toLowerCase(Locale.ROOT).startsWith("https://")) {
      problems.add(name + " must be an https link to the web app (e.g. https://dukkan.de/" + path + "), not '" + url + "'");
    }
  }
}
