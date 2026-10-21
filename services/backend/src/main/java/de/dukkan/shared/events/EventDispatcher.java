package de.dukkan.shared.events;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Hands an event to every interested {@link EventHandler}, each in its own transaction together
 * with its {@code processed_event} marker, so redelivered events are skipped and a failing handler
 * does not undo the others. Throws if any handler failed (the transport then retries).
 */
@Component
public class EventDispatcher {

  private static final Logger LOG = LoggerFactory.getLogger(EventDispatcher.class);

  private final List<EventHandler> handlers;
  private final JdbcClient jdbc;
  private final TransactionTemplate transactions;
  private final Clock clock;

  public EventDispatcher(List<EventHandler> handlers, JdbcClient jdbc, PlatformTransactionManager transactionManager, Clock clock) {
    this.handlers = List.copyOf(handlers);
    this.jdbc = jdbc;
    // Own transaction per handler, also when called from the publisher's transaction (in-process).
    this.transactions = new TransactionTemplate(transactionManager);
    this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.clock = clock;
  }

  public void dispatch(OutboxEvent event) {
    RuntimeException failure = null;
    for (EventHandler handler : handlers) {
      if (!handler.types().contains(event.type())) {
        continue;
      }
      try {
        transactions.executeWithoutResult(
            status -> {
              int inserted =
                  jdbc.sql(
                          "INSERT INTO processed_event (event_id, handler, processed_at) VALUES (:id, :handler, :now)"
                              + " ON CONFLICT DO NOTHING")
                      .param("id", event.id())
                      .param("handler", handler.name())
                      .param("now", Timestamp.from(clock.instant()))
                      .update();
              if (inserted == 1) {
                handler.handle(event);
              }
            });
      } catch (RuntimeException e) {
        LOG.warn("Handler {} failed for event {} ({}): {}", handler.name(), event.id(), event.type(), e.toString());
        failure = failure == null ? e : failure;
      }
    }
    if (failure != null) {
      throw failure;
    }
  }
}
