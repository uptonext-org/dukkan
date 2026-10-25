package de.dukkan.market.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Contract schema {@code OrderSettings}: what a merchant controls about ordering. A {@code null}
 * number means the platform default ({@link OrderSettingsView#defaults()}); an empty {@code
 * deliveryHours} list means deliveries during the opening hours. Customers only see the effect
 * (no order buttons, a pause or holiday notice, fewer slots), never the numbers.
 */
public record OrderSettings(
    @NotNull Boolean ordersEnabled,
    @Nullable Instant pausedUntil,
    @Nullable LocalDate closedFrom,
    @Nullable LocalDate closedUntil,
    @Nullable @Min(1) @Max(99) Integer deliveryCapacity,
    @Nullable @Min(1) @Max(99) Integer pickupCapacity,
    @Nullable @Min(0) @Max(2880) Integer deliveryLeadMinutes,
    @Nullable @Min(0) @Max(2880) Integer pickupLeadMinutes,
    @Nullable @Min(1) @Max(14) Integer daysAhead,
    @NotNull @Size(max = 28) List<@Valid OpeningHours> deliveryHours,
    @NotNull Boolean autoAccept,
    @NotNull @Size(min = 1, max = 2) List<PaymentMethod> paymentMethods,
    @Nullable @Min(1) @Max(99) Integer defaultMaxOrderQuantity) {

  /** How a customer can pay at handover (platform payment mode ON_SITE). */
  public enum PaymentMethod {
    CASH,
    CARD
  }

  /** The platform values used where a setting is {@code null}. */
  public record Defaults(
      int deliveryCapacity, int pickupCapacity, int deliveryLeadMinutes, int pickupLeadMinutes, int daysAhead, int maxOrderQuantity) {}
}
