package de.dukkan.notification.application;

/** Sends plain-text e-mails (SMTP in production). */
public interface Mailer {

  /** False without an SMTP server (development): mails are skipped. */
  boolean enabled();

  /** Sends now; throws when the server does not take the mail, so the event is retried. */
  void send(String to, String subject, String body);
}
