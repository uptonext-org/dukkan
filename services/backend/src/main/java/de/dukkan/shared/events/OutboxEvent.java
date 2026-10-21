package de.dukkan.shared.events;

import java.time.Instant;
import java.util.UUID;

/**
 * A domain event as stored in the outbox and sent to consumers. {@code type} is a dotted name
 * such as {@code order.placed} (also the RabbitMQ routing key); {@code payload} is JSON.
 */
public record OutboxEvent(UUID id, String type, UUID aggregateId, String payload, Instant createdAt) {}
