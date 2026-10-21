package de.dukkan.merchant.application;

import de.dukkan.catalog.application.CatalogProduct;
import de.dukkan.catalog.application.CatalogService;
import de.dukkan.catalog.application.Listing;
import de.dukkan.catalog.application.ListingDraft;
import de.dukkan.catalog.application.ListingUpdate;
import de.dukkan.i18n.application.ContentLocale;
import de.dukkan.market.application.MarketInfo;
import de.dukkan.market.application.MarketProfile;
import de.dukkan.market.application.MarketService;
import de.dukkan.market.application.MerchantMarket;
import de.dukkan.order.application.OrderService;
import de.dukkan.order.application.OrderView;
import de.dukkan.promotion.application.PromotionDraft;
import de.dukkan.promotion.application.PromotionService;
import de.dukkan.promotion.application.PromotionView;
import de.dukkan.shared.api.PageResponse;
import de.dukkan.shared.error.NotFoundException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Merchant use cases: a thin facade over the market, catalog, promotion and order services that
 * first checks ownership ({@code market_owner}). Markets, listings, promotions and orders of other
 * merchants answer {@code NOT_FOUND}.
 */
@Service
@Transactional
public class MerchantService {

  private final MarketService markets;
  private final CatalogService catalog;
  private final PromotionService promotions;
  private final OrderService orders;

  public MerchantService(MarketService markets, CatalogService catalog, PromotionService promotions, OrderService orders) {
    this.markets = markets;
    this.catalog = catalog;
    this.promotions = promotions;
    this.orders = orders;
  }

  @Transactional(readOnly = true)
  public List<MerchantMarket> myMarkets(UUID userId, ContentLocale locale) {
    return markets.owned(userId, locale);
  }

  public MerchantMarket createMarket(UUID userId, MarketProfile profile, ContentLocale locale) {
    return markets.create(userId, profile, locale);
  }

  public MerchantMarket updateMarket(UUID userId, UUID marketId, MarketProfile profile, ContentLocale locale) {
    requireOwner(userId, marketId);
    return markets.update(marketId, profile, locale);
  }

  /**
   * Shared catalog search. The contract has no market parameter here, so {@code alreadyListed} is
   * only computed when the merchant owns exactly one market (null otherwise).
   */
  @Transactional(readOnly = true)
  public PageResponse<CatalogProduct> searchCatalog(
      UUID userId,
      @Nullable String q,
      @Nullable String cuisine,
      @Nullable UUID categoryId,
      int page,
      int size,
      ContentLocale locale) {
    List<UUID> owned = markets.ownedMarketIds(userId);
    UUID context = owned.size() == 1 ? owned.getFirst() : null;
    return catalog.sharedCatalog(q, cuisine, categoryId, context, page, size, locale);
  }

  @Transactional(readOnly = true)
  public PageResponse<Listing> listings(UUID userId, UUID marketId, @Nullable String q, int page, int size, ContentLocale locale) {
    requireOwner(userId, marketId);
    return catalog.merchantListings(marketId, q, page, size, locale);
  }

  public Listing createListing(UUID userId, UUID marketId, ListingDraft draft, ContentLocale locale) {
    requireOwner(userId, marketId);
    return catalog.createListing(marketId, draft, locale);
  }

  public Listing updateListing(UUID userId, UUID marketId, UUID listingId, ListingUpdate update, ContentLocale locale) {
    requireOwner(userId, marketId);
    return catalog.updateListing(marketId, listingId, update, locale);
  }

  public void deleteListing(UUID userId, UUID marketId, UUID listingId) {
    requireOwner(userId, marketId);
    catalog.deleteListing(marketId, listingId);
  }

  @Transactional(readOnly = true)
  public List<PromotionView> promotions(UUID userId, UUID marketId, ContentLocale locale) {
    MarketInfo market = requireOwner(userId, marketId);
    return promotions.listAll(marketId, locale, market.primaryLocale());
  }

  public PromotionView createPromotion(UUID userId, UUID marketId, PromotionDraft draft, ContentLocale locale) {
    MarketInfo market = requireOwner(userId, marketId);
    Set<UUID> valid = catalog.activeListingIdsOfMarket(marketId, draft.listingIds());
    if (!valid.containsAll(draft.listingIds())) {
      throw new NotFoundException("One or more products are not listed in this market.");
    }
    return promotions.create(marketId, draft, locale, market.primaryLocale());
  }

  public void endPromotion(UUID userId, UUID marketId, UUID promotionId) {
    requireOwner(userId, marketId);
    promotions.end(marketId, promotionId);
  }

  @Transactional(readOnly = true)
  public List<OrderView> orders(UUID userId, UUID marketId, @Nullable String status, ContentLocale locale) {
    requireOwner(userId, marketId);
    return orders.listForMarket(marketId, status, locale);
  }

  public OrderView advanceOrder(UUID userId, UUID orderId, String status, ContentLocale locale) {
    UUID marketId = orders.marketOf(orderId).orElseThrow(() -> new NotFoundException("Order not found."));
    if (!markets.isOwner(marketId, userId)) {
      throw new NotFoundException("Order not found.");
    }
    return orders.advanceStatus(orderId, status, locale);
  }

  private MarketInfo requireOwner(UUID userId, UUID marketId) {
    if (!markets.isOwner(marketId, userId)) {
      throw new NotFoundException("Market not found.");
    }
    return markets.info(marketId);
  }
}
