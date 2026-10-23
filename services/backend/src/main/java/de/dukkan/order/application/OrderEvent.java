package de.dukkan.order.application;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Payload of {@code order.placed}, {@code order.status_changed} and {@code order.items_adjusted}
 * events (outbox, B-37). {@code actor} is CUSTOMER (placed, cancelled) or MERCHANT (status
 * changes, adjusted items). For adjusted items, {@code previousTotalCents} is the total before and
 * {@code shortItems} the number of lines handed over with fewer units than ordered (null otherwise).
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
    Instant occurredAt,
    @Nullable Long previousTotalCents,
    @Nullable Integer shortItems) {

  public static final String PLACED = "order.placed";
  public static final String STATUS_CHANGED = "order.status_changed";
  public static final String ITEMS_ADJUSTED = "order.items_adjusted";
}
