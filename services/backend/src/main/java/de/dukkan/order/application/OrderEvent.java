package de.dukkan.order.application;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Payload of {@code order.placed} and {@code order.status_changed} events (outbox, B-37). {@code
 * actor} is CUSTOMER (placed, cancelled) or MERCHANT (status changes).
 */
public record OrderEvent(
    UUID orderId,
    String orderNumber,
    UUID marketId,
    String marketName,
    UUID customerId,
    String status,
    @Nullable String previousStatus,
    String fulfilmentType,
    long totalCents,
    String actor,
    Instant occurredAt) {

  public static final String PLACED = "order.placed";
  public static final String STATUS_CHANGED = "order.status_changed";
}
