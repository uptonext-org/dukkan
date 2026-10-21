package de.dukkan.fulfilment.application;

import de.dukkan.shared.domain.FulfilmentType;
import org.jspecify.annotations.Nullable;

/**
 * The delivery fee rule: pickup is free; delivery costs the market's fee unless the merchandise
 * value (after discounts, before the fee) reaches {@code freeDeliveryFrom}.
 */
public final class DeliveryFee {

  private DeliveryFee() {}

  public static long feeCents(
      @Nullable FulfilmentType type, long deliveryFeeCents, @Nullable Long freeDeliveryFromCents, long merchandiseCents) {
    if (type != FulfilmentType.DELIVERY) {
      return 0;
    }
    if (freeDeliveryFromCents != null && merchandiseCents >= freeDeliveryFromCents) {
      return 0;
    }
    return deliveryFeeCents;
  }
}
