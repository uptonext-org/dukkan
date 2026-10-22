package de.dukkan.order.application;

import de.dukkan.catalog.application.CatalogService;
import de.dukkan.catalog.application.ProductSummary;
import de.dukkan.i18n.application.ContentLocale;
import de.dukkan.market.application.MarketInfo;
import de.dukkan.market.application.MarketService;
import de.dukkan.market.application.MarketSummary;
import de.dukkan.order.infrastructure.PurchaseStatistics;
import de.dukkan.shared.api.GeoPoint;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Meine Märkte" on the start page: the markets the user ordered from, with the products bought
 * there most recently, plus markets the app remembers as visited (also for guests). Only active
 * markets are returned.
 */
@Service
@Transactional(readOnly = true)
public class RecentMarketsService {

  public static final int MAX_MARKETS = 20;
  static final int RECENT_PRODUCTS = 8;

  private final PurchaseStatistics statistics;
  private final MarketService markets;
  private final CatalogService catalog;

  public RecentMarketsService(PurchaseStatistics statistics, MarketService markets, CatalogService catalog) {
    this.statistics = statistics;
    this.markets = markets;
    this.catalog = catalog;
  }

  /**
   * Ordered markets first (most recent order first), then visited markets without orders in the
   * order given (most recently visited first); the app merges both by its own visit times.
   */
  public List<RecentMarket> recent(@Nullable UUID userId, List<UUID> visited, @Nullable GeoPoint reference, ContentLocale locale) {
    Map<UUID, PurchaseStatistics.MarketActivity> ordered = new LinkedHashMap<>();
    if (userId != null) {
      for (PurchaseStatistics.MarketActivity activity : statistics.orderedMarkets(userId, MAX_MARKETS)) {
        ordered.put(activity.marketId(), activity);
      }
    }
    Set<UUID> ids = new LinkedHashSet<>(ordered.keySet());
    visited.stream().limit(MAX_MARKETS).forEach(ids::add);
    Map<UUID, MarketSummary> summaries = markets.summaries(ids, reference, locale);

    List<RecentMarket> result = new ArrayList<>();
    for (UUID id : ids) {
      MarketSummary summary = summaries.get(id);
      if (summary == null || !markets.findInfo(id).map(MarketInfo::isActive).orElse(false)) {
        continue;
      }
      PurchaseStatistics.MarketActivity activity = ordered.get(id);
      if (activity == null) {
        result.add(new RecentMarket(summary, null, null, 0, List.of()));
        continue;
      }
      List<UUID> bought = statistics.recentlyBoughtListings(userId, id, RECENT_PRODUCTS * 2);
      List<ProductSummary> products =
          catalog.summaries(bought, locale, userId).stream().filter(ProductSummary::available).limit(RECENT_PRODUCTS).toList();
      result.add(new RecentMarket(summary, activity.lastOrderAt(), activity.lastOrderId(), activity.orderCount(), products));
      if (result.size() == MAX_MARKETS) {
        break;
      }
    }
    return result;
  }
}
