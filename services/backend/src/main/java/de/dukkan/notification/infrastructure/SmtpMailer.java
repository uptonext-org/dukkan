package de.dukkan.notification.infrastructure;

import de.dukkan.notification.application.Mailer;
import de.dukkan.shared.config.DukkanProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Order e-mails over the same SMTP server and sender as the account mails. */
@Component
final class SmtpMailer implements Mailer {

  private final ObjectProvider<JavaMailSender> sender;
  private final String from;

  SmtpMailer(ObjectProvider<JavaMailSender> sender, DukkanProperties properties) {
    this.sender = sender;
    this.from = properties.security().auth().mailFrom();
  }

  @Override
  public boolean enabled() {
    return sender.getIfAvailable() != null;
  }

  @Override
  public void send(String to, String subject, String body) {
    JavaMailSender mail = sender.getObject();
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(to);
    message.setSubject(subject);
    message.setText(body);
    mail.send(message);
  }
}
