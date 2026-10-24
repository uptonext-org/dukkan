package de.dukkan.support;

import de.dukkan.notification.application.Mailer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Test mailer: keeps every order e-mail instead of sending it. */
public final class CapturingMailer implements Mailer {

  public record Sent(String to, String subject, String body) {}

  private final List<Sent> sent = new CopyOnWriteArrayList<>();

  @Override
  public boolean enabled() {
    return true;
  }

  @Override
  public void send(String to, String subject, String body) {
    sent.add(new Sent(to, subject, body));
  }

  public List<Sent> to(String email) {
    return sent.stream().filter(m -> m.to().equals(email)).toList();
  }
}
