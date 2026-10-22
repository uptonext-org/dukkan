package de.dukkan.support;

import de.dukkan.identity.application.AccountMailer;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;

/** Test mailer: remembers the last reset link per email instead of sending mail. */
public final class CapturingAccountMailer implements AccountMailer {

  private final Map<String, String> links = new ConcurrentHashMap<>();

  @Override
  public void sendPasswordReset(String email, String firstName, @Nullable String locale, String link) {
    links.put(email, link);
  }

  private final Map<String, String> verificationLinks = new ConcurrentHashMap<>();

  @Override
  public void sendEmailVerification(String email, String firstName, @Nullable String locale, String link) {
    verificationLinks.put(email, link);
  }

  public Optional<String> lastVerificationLink(String email) {
    return Optional.ofNullable(verificationLinks.get(email));
  }

  public Optional<String> lastLink(String email) {
    return Optional.ofNullable(links.get(email));
  }

  /** The {@code token} query parameter of the last link. */
  public Optional<String> lastToken(String email) {
    return lastLink(email).map(link -> java.net.URLDecoder.decode(link.substring(link.indexOf("token=") + 6), java.nio.charset.StandardCharsets.UTF_8));
  }
}
