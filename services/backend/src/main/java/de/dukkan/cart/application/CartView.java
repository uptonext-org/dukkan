package de.dukkan.cart.application;

import de.dukkan.delivery.application.SlotView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A cart with server-computed totals. */
public record CartView(
    UUID id,
    List<CartLineView> lines,
    int itemCount,
    long subtotalCents,
    long discountCents,
    long deliveryFeeCents,
    long totalCents,
    long minimumOrderCents,
    @Nullable ReservationView reservation) {

  /** The reserved slot and the address it is reserved for. */
  public record ReservationView(SlotView slot, UUID addressId, Instant expiresAt) {}

  /** Merchandise value after discounts, excluding the delivery fee. */
  public long merchandiseTotalCents() {
    return subtotalCents - discountCents;
  }

  public boolean meetsMinimumOrderValue() {
    return merchandiseTotalCents() >= minimumOrderCents;
  }
}
