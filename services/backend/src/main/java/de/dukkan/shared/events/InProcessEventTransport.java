package de.dukkan.shared.events;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default transport ({@code dukkan.events.transport=in-process}): the publisher hands events
 * straight to the handlers of this instance. No broker needed (development, tests, one replica).
 */
@Component
@ConditionalOnProperty(name = "dukkan.events.transport", havingValue = "in-process", matchIfMissing = true)
class InProcessEventTransport implements EventTransport {

  private final EventDispatcher dispatcher;

  InProcessEventTransport(EventDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  @Override
  public void send(OutboxEvent event) {
    dispatcher.dispatch(event);
  }
}
