package de.dukkan.market.application;

import de.dukkan.shared.domain.FulfilmentType;
import de.dukkan.shared.api.GeoPoint;
import java.time.Instant;
import java.time.LocalDate;
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
    List<OpeningInterval> openingHours,
    Ordering ordering) {

  /**
   * The merchant's ordering settings with the platform defaults already applied. {@code
   * deliveryHours} is never empty when the market has opening hours (it falls back to them).
   */
  public record Ordering(
      boolean enabled,
      @Nullable Instant pausedUntil,
      @Nullable LocalDate closedFrom,
      @Nullable LocalDate closedUntil,
      int deliveryCapacity,
      int pickupCapacity,
      int deliveryLeadMinutes,
      int pickupLeadMinutes,
      int daysAhead,
      List<OpeningInterval> deliveryHours,
      boolean autoAccept,
      List<OrderSettings.PaymentMethod> paymentMethods,
      int defaultMaxOrderQuantity) {

    /** Whether the market is closed (holiday) on a local date. */
    public boolean closedOn(LocalDate date) {
      return closedFrom != null && closedUntil != null && !date.isBefore(closedFrom) && !date.isAfter(closedUntil);
    }

    public boolean paused(Instant now) {
      return pausedUntil != null && now.isBefore(pausedUntil);
    }
  }

  public boolean isActive() {
    return "ACTIVE".equals(status);
  }

  public boolean offers(FulfilmentType type) {
    return type == FulfilmentType.DELIVERY ? delivery : pickup;
  }

  /** New orders are possible now: the market is active, ordering is switched on and not paused. */
  public boolean acceptsOrders(Instant now) {
    return isActive() && ordering.enabled() && !ordering.paused(now);
  }
}
