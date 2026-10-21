package de.dukkan.notification.application;

import java.util.List;
import java.util.Map;

/** Port to a push service (Firebase Cloud Messaging for Android and iOS). */
public interface PushGateway {

  /** One notification as shown by the operating system; {@code data} goes to the app. */
  record PushMessage(String title, String body, Map<String, String> data) {}

  boolean enabled();

  /**
   * Sends the message to each device; never throws. Returns the tokens the service reported as no
   * longer valid (app uninstalled, token rotated), which the caller deletes.
   */
  List<String> send(List<String> deviceTokens, PushMessage message);
}
