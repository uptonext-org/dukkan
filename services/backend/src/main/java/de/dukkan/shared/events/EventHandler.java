package de.dukkan.shared.events;

import java.util.Set;

/**
 * Consumes events of the given types. Each event reaches each handler at most once (recorded in
 * {@code processed_event}); a failing handler is retried, so it must not have side effects outside
 * its transaction other than idempotent ones (e.g. a push that may arrive twice).
 */
public interface EventHandler {

  /** Stable name, part of the idempotency key; never rename a deployed handler. */
  String name();

  Set<String> types();

  void handle(OutboxEvent event);
}
