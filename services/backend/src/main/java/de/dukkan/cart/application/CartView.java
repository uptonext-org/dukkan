package de.dukkan.cart.application;

import de.dukkan.fulfilment.application.SlotView;
import de.dukkan.shared.domain.FulfilmentType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A cart in one market with server-computed totals. */
public record CartView(
    UUID id,
    UUID marketId,
    List<CartLineView> lines,
    int itemCount,
    long subtotalCents,
    long discountCents,
    long deliveryFeeCents,
    long totalCents,
    long minimumOrderCents,
    @Nullable ReservationView reservation,
    Instant updatedAt) {

  /** The reserved slot; {@code addressId} is set for delivery. */
  public record ReservationView(FulfilmentType type, SlotView slot, @Nullable UUID addressId, Instant expiresAt) {}

  /** Merchandise value after discounts, excluding the delivery fee. */
  public long merchandiseTotalCents() {
    return subtotalCents - discountCents;
  }

  public boolean meetsMinimumOrderValue() {
    return merchandiseTotalCents() >= minimumOrderCents;
  }
}
