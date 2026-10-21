package de.dukkan.identity.infrastructure;

import de.dukkan.identity.application.AccountMailer;
import de.dukkan.shared.config.DukkanProperties;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Plain-text account emails over SMTP ({@code spring.mail.host}, {@code spring.mail.username}, …).
 * Without SMTP the mail is not sent; with {@code dukkan.security.auth.log-reset-links} (local
 * profile) the link is logged instead so that development works without a mail server.
 */
@Component
final class MailAccountMailer implements AccountMailer {

  private static final Logger LOG = LoggerFactory.getLogger(MailAccountMailer.class);

  private final ObjectProvider<JavaMailSender> sender;
  private final String from;
  private final boolean logLinks;
  private final long validMinutes;

  MailAccountMailer(ObjectProvider<JavaMailSender> sender, DukkanProperties properties) {
    this.sender = sender;
    this.from = properties.security().auth().mailFrom();
    this.logLinks = properties.security().auth().logResetLinks();
    this.validMinutes = properties.security().auth().passwordResetTtl().toMinutes();
  }

  @Override
  public void sendPasswordReset(String email, String firstName, @Nullable String locale, String link) {
    JavaMailSender mail = sender.getIfAvailable();
    if (mail == null) {
      if (logLinks) {
        LOG.info("[dev] Password reset link for {}: {}", email, link);
      } else {
        LOG.warn("Password reset requested but no SMTP server is configured (spring.mail.host); no mail sent");
      }
      return;
    }
    Text text = text(locale, firstName, link, validMinutes);
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(email);
    message.setSubject(text.subject());
    message.setText(text.body());
    // In the background, so that the response time does not reveal whether the account exists.
    Thread.ofVirtual()
        .name("password-reset-mail")
        .start(
            () -> {
              try {
                mail.send(message);
              } catch (MailException e) {
                // The API answers 202 either way; the user can ask again.
                LOG.error("Could not send the password reset mail: {}", e.getMessage());
              }
            });
  }

  record Text(String subject, String body) {}

  static Text text(@Nullable String locale, String firstName, String link, long minutes) {
    return switch (locale == null ? "de" : locale) {
      case "en" ->
          new Text(
              "Reset your Dukkan password",
              "Hello " + firstName + ",\n\nopen this link on your phone to choose a new password:\n" + link
                  + "\n\nThe link is valid for " + minutes + " minutes. If you did not ask for it, ignore this email; your password stays the same.\n\nDukkan");
      case "ar" ->
          new Text(
              "إعادة تعيين كلمة مرور دكان",
              "مرحباً " + firstName + "،\n\nيُرجى فتح هذا الرابط على الهاتف لاختيار كلمة مرور جديدة:\n" + link
                  + "\n\nالرابط صالح لمدة " + minutes + " دقيقة. إذا لم يتم طلبه، يمكن تجاهل هذه الرسالة؛ ولن تتغير كلمة المرور.\n\nدكان");
      default ->
          new Text(
              "Dukkan: Passwort zurücksetzen",
              "Hallo " + firstName + ",\n\nöffne diesen Link auf deinem Handy, um ein neues Passwort zu wählen:\n" + link
                  + "\n\nDer Link ist " + minutes + " Minuten gültig. Wenn du das nicht angefordert hast, ignoriere diese E-Mail; dein Passwort bleibt unverändert.\n\nDukkan");
    };
  }
}
