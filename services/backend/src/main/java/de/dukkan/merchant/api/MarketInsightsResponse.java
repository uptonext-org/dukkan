package de.dukkan.merchant.api;

import de.dukkan.order.application.MarketInsights;
import de.dukkan.shared.api.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Contract schema {@code MarketInsights}. */
record MarketInsightsResponse(
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

  record Totals(long orders, Money revenue, @Nullable Money averageOrderValue, Money discounts) {
    static Totals of(MarketInsights.Totals t) {
      return new Totals(t.orders(), Money.eur(t.revenueCents()), t.orders() == 0 ? null : Money.eur(Math.round((double) t.revenueCents() / t.orders())), Money.eur(t.discountCents()));
    }
  }

  record Day(LocalDate date, long orders, Money revenue) {}

  record TopProduct(UUID listingId, String name, @Nullable String unitSize, @Nullable String imageUrl, long quantity, Money revenue) {}

  static MarketInsightsResponse of(MarketInsights i) {
    return new MarketInsightsResponse(
        i.from(),
        i.to(),
        Totals.of(i.totals()),
        Totals.of(i.previous()),
        i.cancelledOrders(),
        i.rejectedOrders(),
        i.pickupOrders(),
        i.deliveryOrders(),
        i.customers(),
        i.returningCustomers(),
        i.daily().stream().map(d -> new Day(d.date(), d.orders(), Money.eur(d.revenueCents()))).toList(),
        i.ordersByHour(),
        i.topProducts().stream().map(p -> new TopProduct(p.listingId(), p.name(), p.unitSize(), p.imageUrl(), p.quantity(), Money.eur(p.revenueCents()))).toList());
  }
}
