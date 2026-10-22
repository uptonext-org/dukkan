package de.dukkan.order.application;

import de.dukkan.catalog.application.CatalogService;
import de.dukkan.catalog.application.ListingInfo;
import de.dukkan.i18n.application.ContentLocale;
import de.dukkan.order.infrastructure.SalesStatistics;
import de.dukkan.shared.time.DukkanTime;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The merchant's sales overview: the last {@code days} days including today, compared with the
 * {@code days} days before up to the same time of day.
 */
@Service
@Transactional(readOnly = true)
public class SalesInsightsService {

  public static final int TOP_PRODUCTS = 10;

  private final SalesStatistics statistics;
  private final CatalogService catalog;
  private final Clock clock;

  public SalesInsightsService(SalesStatistics statistics, CatalogService catalog, Clock clock) {
    this.statistics = statistics;
    this.catalog = catalog;
    this.clock = clock;
  }

  public MarketInsights insights(UUID marketId, int days, ContentLocale locale) {
    Instant now = clock.instant();
    LocalDate to = LocalDate.ofInstant(now, DukkanTime.ZONE);
    LocalDate from = to.minusDays(days - 1L);
    Instant start = startOf(from);
    Instant end = startOf(to.plusDays(1));
    // Today is not over yet: compare with the period before only up to the same time of day.
    Instant previousStart = startOf(from.minusDays(days));
    Instant previousEnd = now.atZone(DukkanTime.ZONE).minusDays(days).toInstant();

    SalesStatistics.Summary summary = statistics.summary(marketId, start, end);
    SalesStatistics.Summary previous = statistics.summary(marketId, previousStart, previousEnd);

    Map<LocalDate, SalesStatistics.DayRow> byDay =
        statistics.daily(marketId, start, end).stream().collect(Collectors.toMap(SalesStatistics.DayRow::date, Function.identity()));
    List<MarketInsights.Day> daily = new ArrayList<>(days);
    for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
      SalesStatistics.DayRow row = byDay.get(day);
      daily.add(new MarketInsights.Day(day, row == null ? 0 : row.orders(), row == null ? 0 : row.revenueCents()));
    }

    List<SalesStatistics.ProductRow> top = statistics.topProducts(marketId, start, end, TOP_PRODUCTS);
    Map<UUID, ListingInfo> infos = catalog.listingInfos(top.stream().map(SalesStatistics.ProductRow::listingId).toList(), locale);
    List<MarketInsights.TopProduct> topProducts =
        top.stream()
            .map(
                row -> {
                  ListingInfo info = infos.get(row.listingId());
                  // The current name in the reader's language; the order snapshot if the listing is gone.
                  return info == null
                      ? new MarketInsights.TopProduct(row.listingId(), row.name(), null, null, row.quantity(), row.revenueCents())
                      : new MarketInsights.TopProduct(row.listingId(), info.name(), info.unitSize(), info.imageUrl(), row.quantity(), row.revenueCents());
                })
            .toList();

    return new MarketInsights(
        from,
        to,
        new MarketInsights.Totals(summary.orders(), summary.revenueCents(), summary.discountCents()),
        new MarketInsights.Totals(previous.orders(), previous.revenueCents(), previous.discountCents()),
        summary.cancelled(),
        summary.rejected(),
        summary.pickup(),
        summary.delivery(),
        summary.customers(),
        summary.returningCustomers(),
        daily,
        Arrays.stream(statistics.ordersByHour(marketId, start, end)).boxed().toList(),
        topProducts);
  }

  private static Instant startOf(LocalDate day) {
    return day.atStartOfDay(DukkanTime.ZONE).toInstant();
  }
}
