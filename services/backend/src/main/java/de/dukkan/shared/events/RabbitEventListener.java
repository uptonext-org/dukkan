package de.dukkan.shared.events;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.time.Instant;
import java.util.UUID;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

/** Consumes {@code dukkan.backend.events} and hands each event to the local handlers. */
final class RabbitEventListener {

  private final EventDispatcher dispatcher;

  RabbitEventListener(EventDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  @RabbitListener(queues = RabbitEventsConfiguration.QUEUE)
  void onMessage(Message message) {
    var properties = message.getMessageProperties();
    Object aggregate = properties.getHeaders().get("x-aggregate-id");
    Object created = properties.getHeaders().get("x-created-at");
    dispatcher.dispatch(
        new OutboxEvent(
            UUID.fromString(properties.getMessageId()),
            properties.getType(),
            UUID.fromString(String.valueOf(aggregate)),
            new String(message.getBody(), UTF_8),
            created == null ? Instant.EPOCH : Instant.parse(String.valueOf(created))));
  }
}
