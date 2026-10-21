package de.dukkan.merchant.application;

import de.dukkan.capture.application.ProductCaptureService;
import de.dukkan.capture.application.ProductDraftInput;
import de.dukkan.capture.application.ProductDraftResult;
import de.dukkan.catalog.application.CatalogProduct;
import de.dukkan.catalog.application.CatalogService;
import de.dukkan.catalog.application.Listing;
import de.dukkan.catalog.application.ListingDraft;
import de.dukkan.catalog.application.ListingImport;
import de.dukkan.catalog.application.ListingUpdate;
import de.dukkan.i18n.application.ContentLocale;
import de.dukkan.i18n.application.LocaleService;
import de.dukkan.i18n.application.SupportedLocale;
import de.dukkan.market.application.MarketInfo;
import de.dukkan.market.application.MarketProfile;
import de.dukkan.market.application.MarketService;
import de.dukkan.market.application.MerchantMarket;
import de.dukkan.media.application.MediaPurpose;
import de.dukkan.media.application.MediaService;
import de.dukkan.media.application.StoredMedia;
import de.dukkan.order.application.OrderService;
import de.dukkan.order.application.OrderView;
import de.dukkan.promotion.application.PromotionDraft;
import de.dukkan.promotion.application.PromotionService;
import de.dukkan.promotion.application.PromotionView;
import de.dukkan.shared.api.PageResponse;
import de.dukkan.shared.error.ContentRejectedException;
import de.dukkan.shared.error.NotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.InputStreamSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
  private final MediaService media;
  private final ProductCaptureService capture;
  private final LocaleService locales;

  public MerchantService(
      MarketService markets,
      CatalogService catalog,
      PromotionService promotions,
      OrderService orders,
      MediaService media,
      ProductCaptureService capture,
      LocaleService locales) {
    this.markets = markets;
    this.catalog = catalog;
    this.promotions = promotions;
    this.orders = orders;
    this.media = media;
    this.capture = capture;
    this.locales = locales;
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
   * Shared catalog search. {@code alreadyListed} is computed for {@code marketId} (which must be
   * owned by the caller) or, without it, for the merchant's only market; otherwise it is null.
   */
  @Transactional(readOnly = true)
  public PageResponse<CatalogProduct> searchCatalog(
      UUID userId,
      @Nullable String q,
      @Nullable String cuisine,
      @Nullable UUID categoryId,
      @Nullable UUID marketId,
      int page,
      int size,
      ContentLocale locale) {
    UUID context;
    if (marketId != null) {
      requireOwner(userId, marketId);
      context = marketId;
    } else {
      List<UUID> owned = markets.ownedMarketIds(userId);
      context = owned.size() == 1 ? owned.getFirst() : null;
    }
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

  /** Bulk import from a CSV file (B-31); see {@link ListingCsv} and {@link CatalogService#importListings}. */
  public ListingImport.Result importListings(UUID userId, UUID marketId, byte[] csv, boolean dryRun) {
    requireOwner(userId, marketId);
    return catalog.importListings(marketId, ListingCsv.parse(csv, locales.enabled().stream().map(SupportedLocale::code).toList()), dryRun);
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

  /**
   * Stores a photo for one of the caller's markets. Every attempt counts against the upload rate
   * limit; at most {@code maxBytes + 1} bytes are read, so an oversized stream is never buffered.
   */
  @Transactional(readOnly = true)
  public StoredMedia uploadMedia(UUID userId, UUID marketId, MediaPurpose purpose, long declaredSize, InputStreamSource content) {
    media.checkRateLimit(userId);
    requireOwner(userId, marketId);
    long max = media.maxBytes();
    if (declaredSize > max) {
      throw ContentRejectedException.tooLarge("The file is larger than " + max + " bytes.");
    }
    byte[] bytes;
    try (InputStream in = content.getInputStream()) {
      bytes = in.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, max + 1));
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read upload", e);
    }
    return media.store(userId, purpose, bytes);
  }

  /**
   * AI product capture for one of the caller's markets. Runs outside a transaction: the language
   * model may take several seconds, and no connection should be held meanwhile.
   */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public ProductDraftResult draftProduct(UUID userId, UUID marketId, ProductDraftInput input, ContentLocale locale) {
    MarketInfo market = requireOwner(userId, marketId);
    return capture.draft(userId, marketId, market.primaryLocale(), input, locale);
  }

  private MarketInfo requireOwner(UUID userId, UUID marketId) {
    if (!markets.isOwner(marketId, userId)) {
      throw new NotFoundException("Market not found.");
    }
    return markets.info(marketId);
  }
}
