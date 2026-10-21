package de.dukkan.shared.events;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * Publishes an event to exchange {@code dukkan.events} with the event type as routing key and
 * waits for the broker's publisher confirm, so the outbox row is only marked as sent once the
 * broker has the message (persistent, unroutable messages are returned and count as failures).
 */
final class RabbitEventTransport implements EventTransport {

  private static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(10);

  private final RabbitTemplate template;

  RabbitEventTransport(RabbitTemplate template) {
    this.template = template;
  }

  @Override
  public void send(OutboxEvent event) {
    Message message =
        MessageBuilder.withBody(event.payload().getBytes(UTF_8))
            .setContentType("application/json")
            .setContentEncoding("UTF-8")
            .setMessageId(event.id().toString())
            .setType(event.type())
            .setHeader("x-aggregate-id", event.aggregateId().toString())
            .setHeader("x-created-at", event.createdAt().toString())
            .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
            .build();
    CorrelationData correlation = new CorrelationData(event.id().toString());
    template.send(RabbitEventsConfiguration.EXCHANGE, event.type(), message, correlation);
    try {
      CorrelationData.Confirm confirm = correlation.getFuture().get(CONFIRM_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
      if (!confirm.ack()) {
        throw new IllegalStateException("Broker rejected event " + event.id() + ": " + confirm.reason());
      }
      if (correlation.getReturned() != null) {
        throw new IllegalStateException("Event " + event.id() + " was not routable");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for the broker", e);
    } catch (ExecutionException | TimeoutException e) {
      throw new IllegalStateException("No publisher confirm for event " + event.id(), e);
    }
  }
}
