package de.dukkan.market.application;

import de.dukkan.shared.domain.FulfilmentType;
import de.dukkan.shared.api.GeoPoint;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Market facts other modules need for carts, slots and orders. */
public record MarketInfo(
    UUID id,
    String name,
    String status,
    String primaryLocale,
    GeoPoint location,
    boolean delivery,
    boolean pickup,
    @Nullable Double deliveryRadiusKm,
    long deliveryFeeCents,
    @Nullable Long freeDeliveryFromCents,
    long minimumOrderCents,
    List<OpeningInterval> openingHours) {

  public boolean isActive() {
    return "ACTIVE".equals(status);
  }

  public boolean offers(FulfilmentType type) {
    return type == FulfilmentType.DELIVERY ? delivery : pickup;
  }
}
