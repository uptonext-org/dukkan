package de.dukkan.order.domain;

import de.dukkan.shared.domain.FulfilmentType;
import java.util.Set;

/**
 * Order lifecycle. Merchant transitions: PLACED→ACCEPTED|REJECTED; ACCEPTED→READY_FOR_PICKUP
 * (pickup) or OUT_FOR_DELIVERY (delivery); those→COMPLETED. Customers cancel PLACED or ACCEPTED
 * orders before the slot cutoff.
 */
public enum OrderStatus {
  PLACED,
  ACCEPTED,
  REJECTED,
  READY_FOR_PICKUP,
  OUT_FOR_DELIVERY,
  COMPLETED,
  CANCELLED;

  /** Whether a merchant may move an order of the given fulfilment type from this status to {@code target}. */
  public boolean canAdvanceTo(OrderStatus target, FulfilmentType type) {
    return switch (this) {
      case PLACED -> target == ACCEPTED || target == REJECTED;
      case ACCEPTED ->
          (type == FulfilmentType.PICKUP && target == READY_FOR_PICKUP)
              || (type == FulfilmentType.DELIVERY && target == OUT_FOR_DELIVERY);
      case READY_FOR_PICKUP, OUT_FOR_DELIVERY -> target == COMPLETED;
      case REJECTED, COMPLETED, CANCELLED -> false;
    };
  }

  /** Statuses whose order no longer holds slot capacity. */
  public boolean releasesCapacity() {
    return Set.of(REJECTED, CANCELLED).contains(this);
  }

  public boolean isCancellableByCustomer() {
    return this == PLACED || this == ACCEPTED;
  }
}
