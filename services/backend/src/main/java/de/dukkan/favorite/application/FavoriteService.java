package de.dukkan.favorite.application;

import de.dukkan.catalog.application.CatalogService;
import de.dukkan.catalog.application.CategoryView;
import de.dukkan.catalog.application.ListingInfo;
import de.dukkan.catalog.application.ProductSummary;
import de.dukkan.catalog.application.PurchaseHistory;
import de.dukkan.favorite.infrastructure.FavoriteRepository;
import de.dukkan.i18n.application.ContentLocale;
import de.dukkan.market.application.MarketInfo;
import de.dukkan.market.application.MarketService;
import de.dukkan.market.application.MarketSummary;
import de.dukkan.shared.api.GeoPoint;
import de.dukkan.shared.error.NotFoundException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Favourite markets, favourite listings and the per-market favourites page. */
@Service
@Transactional
public class FavoriteService {

  public static final String MANUAL = "MANUAL";
  public static final String FREQUENTLY_ORDERED = "FREQUENTLY_ORDERED";

  private final FavoriteRepository favorites;
  private final MarketService markets;
  private final CatalogService catalog;
  private final PurchaseHistory purchases;
  private final Clock clock;

  public FavoriteService(
      FavoriteRepository favorites, MarketService markets, CatalogService catalog, PurchaseHistory purchases, Clock clock) {
    this.favorites = favorites;
    this.markets = markets;
    this.catalog = catalog;
    this.purchases = purchases;
    this.clock = clock;
  }

  public void addMarket(UUID userId, UUID marketId) {
    markets.activeInfo(marketId);
    favorites.addMarket(userId, marketId, clock.instant());
  }

  public void removeMarket(UUID userId, UUID marketId) {
    favorites.removeMarket(userId, marketId);
  }

  /** Favourite markets that are still active, most recently added first. */
  @Transactional(readOnly = true)
  public List<MarketSummary> markets(UUID userId, @Nullable GeoPoint reference, ContentLocale locale) {
    List<UUID> ids = favorites.findMarketIds(userId);
    Map<UUID, MarketSummary> summaries = markets.summaries(ids, reference, locale);
    List<MarketSummary> result = new ArrayList<>();
    for (UUID id : ids) {
      MarketSummary summary = summaries.get(id);
      if (summary != null && markets.findInfo(id).map(MarketInfo::isActive).orElse(false)) {
        result.add(summary);
      }
    }
    return result;
  }

  public void addProduct(UUID userId, UUID listingId) {
    ListingInfo listing = catalog.listingInfos(List.of(listingId), ContentLocale.of(null)).get(listingId);
    if (listing == null
        || listing.deleted()
        || !markets.findInfo(listing.marketId()).map(MarketInfo::isActive).orElse(false)) {
      throw new NotFoundException("Product not found.");
    }
    favorites.addProduct(userId, listingId, clock.instant());
  }

  public void removeProduct(UUID userId, UUID listingId) {
    favorites.removeProduct(userId, listingId);
  }

  /**
   * Manual favourites plus frequently ordered listings of one market, grouped by category (groups in
   * category order, items by name).
   */
  @Transactional(readOnly = true)
  public List<FavoriteGroup> marketFavorites(UUID userId, UUID marketId, ContentLocale locale) {
    MarketInfo market = markets.activeInfo(marketId);
    Set<UUID> manual = catalog.activeListingIdsOfMarket(marketId, favorites.findProductIds(userId));
    Set<UUID> all = new LinkedHashSet<>(manual);
    all.addAll(purchases.frequentlyOrderedListingIds(userId, marketId));
    if (all.isEmpty()) {
      return List.of();
    }
    Map<UUID, ListingInfo> infos = catalog.listingInfos(all, locale);
    List<ProductSummary> summaries = catalog.summaries(List.copyOf(all), locale, userId);
    Map<UUID, List<FavoriteItem>> byCategory = new LinkedHashMap<>();
    for (ProductSummary summary : summaries) {
      ListingInfo info = Objects.requireNonNull(infos.get(summary.id()));
      byCategory
          .computeIfAbsent(info.categoryId(), k -> new ArrayList<>())
          .add(new FavoriteItem(summary, manual.contains(summary.id()) ? MANUAL : FREQUENTLY_ORDERED));
    }
    List<FavoriteGroup> groups = new ArrayList<>();
    for (CategoryView category : catalog.categories(byCategory.keySet(), locale, market.primaryLocale())) {
      List<FavoriteItem> items = new ArrayList<>(byCategory.get(category.id()));
      items.sort(Comparator.comparing(i -> i.product().name()));
      groups.add(new FavoriteGroup(category, items));
    }
    return groups;
  }
}
