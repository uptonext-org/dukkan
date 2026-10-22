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
  private final long verificationDays;

  MailAccountMailer(ObjectProvider<JavaMailSender> sender, DukkanProperties properties) {
    this.sender = sender;
    this.from = properties.security().auth().mailFrom();
    this.logLinks = properties.security().auth().logResetLinks();
    this.validMinutes = properties.security().auth().passwordResetTtl().toMinutes();
    this.verificationDays = Math.max(1, properties.security().auth().emailVerificationTtl().toDays());
  }

  @Override
  public void sendPasswordReset(String email, String firstName, @Nullable String locale, String link) {
    send("password-reset", email, link, text(locale, firstName, link, validMinutes));
  }

  @Override
  public void sendEmailVerification(String email, String firstName, @Nullable String locale, String link) {
    send("email-verification", email, link, verificationText(locale, firstName, link, verificationDays));
  }

  private void send(String kind, String email, String link, Text text) {
    JavaMailSender mail = sender.getIfAvailable();
    if (mail == null) {
      if (logLinks) {
        LOG.info("[dev] {} link for {}: {}", kind, email, link);
      } else {
        LOG.warn("{} mail requested but no SMTP server is configured (spring.mail.host); no mail sent", kind);
      }
      return;
    }
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(email);
    message.setSubject(text.subject());
    message.setText(text.body());
    // In the background, so that the response time does not reveal whether the account exists.
    Thread.ofVirtual()
        .name(kind + "-mail")
        .start(
            () -> {
              try {
                mail.send(message);
              } catch (MailException e) {
                // The API answers either way; the user can ask again.
                LOG.error("Could not send the {} mail: {}", kind, e.getMessage());
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

  static Text verificationText(@Nullable String locale, String firstName, String link, long days) {
    return switch (locale == null ? "de" : locale) {
      case "en" ->
          new Text(
              "Confirm your email address for Dukkan",
              "Hello " + firstName + ",\n\nwelcome to Dukkan! Open this link on your phone to confirm your email address:\n" + link
                  + "\n\nThe link is valid for " + days + " days. If you did not create an account, ignore this email.\n\nDukkan");
      case "ar" ->
          new Text(
              "تأكيد البريد الإلكتروني في دكان",
              "مرحباً " + firstName + "،\n\nأهلاً بك في دكان! يُرجى فتح هذا الرابط على الهاتف لتأكيد البريد الإلكتروني:\n" + link
                  + "\n\nالرابط صالح لمدة " + days + " أيام. إذا لم يتم إنشاء حساب، يمكن تجاهل هذه الرسالة.\n\nدكان");
      default ->
          new Text(
              "Dukkan: E-Mail-Adresse bestätigen",
              "Hallo " + firstName + ",\n\nwillkommen bei Dukkan! Öffne diesen Link auf deinem Handy, um deine E-Mail-Adresse zu bestätigen:\n" + link
                  + "\n\nDer Link ist " + days + " Tage gültig. Wenn du kein Konto angelegt hast, ignoriere diese E-Mail.\n\nDukkan");
    };
  }
}
