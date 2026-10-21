package de.dukkan.shared.events;

/** Delivers one event to its consumers; throws when delivery is not confirmed. */
public interface EventTransport {

  void send(OutboxEvent event);
}
