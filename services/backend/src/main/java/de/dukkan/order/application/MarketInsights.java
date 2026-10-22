package de.dukkan.order.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Sales of one market over whole days in Europe/Berlin, {@code from} to {@code to} inclusive.
 * Orders count unless they were cancelled or rejected; amounts are order totals in cents
 * (after discounts, with delivery fees). Only aggregates: nothing identifies a customer.
 */
public record MarketInsights(
    LocalDate from,
    LocalDate to,
    Totals totals,
    Totals previous,
    long cancelledOrders,
    long rejectedOrders,
    long pickupOrders,
    long deliveryOrders,
    long customers,
    long returningCustomers,
    List<Day> daily,
    List<Long> ordersByHour,
    List<TopProduct> topProducts) {

  public record Totals(long orders, long revenueCents, long discountCents) {}

  public record Day(LocalDate date, long orders, long revenueCents) {}

  public record TopProduct(UUID listingId, String name, @Nullable String unitSize, @Nullable String imageUrl, long quantity, long revenueCents) {}
}
