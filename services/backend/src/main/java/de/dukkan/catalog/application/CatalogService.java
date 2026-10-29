package de.dukkan.catalog.application;

import de.dukkan.catalog.domain.ComparisonPrice;
import de.dukkan.catalog.domain.SearchText;
import de.dukkan.catalog.infrastructure.CatalogProductRepository;
import de.dukkan.catalog.infrastructure.CatalogProductRepository.CatalogPage;
import de.dukkan.catalog.infrastructure.CategoryRepository;
import de.dukkan.catalog.infrastructure.CategoryRepository.CategoryRow;
import de.dukkan.catalog.infrastructure.ListingRepository;
import de.dukkan.catalog.infrastructure.ListingRepository.Hit;
import de.dukkan.catalog.infrastructure.ListingRepository.IdPage;
import de.dukkan.catalog.infrastructure.ListingRepository.ListingRow;
import de.dukkan.i18n.application.ContentLocale;
import de.dukkan.i18n.application.LocaleService;
import de.dukkan.i18n.application.TextNormalizer;
import de.dukkan.market.application.MarketDistance;
import de.dukkan.market.application.MarketInfo;
import de.dukkan.market.application.MarketService;
import de.dukkan.market.application.MarketSummary;
import de.dukkan.promotion.application.PricingEngine;
import de.dukkan.promotion.application.PromotionRule;
import de.dukkan.promotion.application.PromotionService;
import de.dukkan.shared.api.GeoPoint;
import de.dukkan.shared.api.Money;
import de.dukkan.shared.api.PageResponse;
import de.dukkan.shared.config.DukkanProperties;
import de.dukkan.shared.error.ConflictException;
import de.dukkan.shared.error.ErrorCodes;
import de.dukkan.shared.events.EventOutbox;
import de.dukkan.shared.error.InvalidRequestException;
import de.dukkan.shared.error.NotFoundException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Categories, listings (a market's offer of a catalog product), language-independent search and
 * the merchant's catalog operations. Product ids in the API are listing ids.
 */
@Service
@Transactional(readOnly = true)
public class CatalogService {

  /** The shared catalog is kept in German first; other languages are optional. */
  public static final String SHARED_CATALOG_LOCALE = "de";

  /** Upper bound of matches considered by the cross-market search before sorting and paging. */
  private static final int NEARBY_CAP = 1000;

  private final ListingRepository listings;
  private final CatalogProductRepository products;
  private final CategoryRepository categories;
  private final MarketService markets;
  private final PromotionService promotions;
  private final ListingFavorites favorites;
  private final PurchaseHistory purchases;
  private final LocaleService locales;
  private final Clock clock;
  private final DukkanProperties properties;
  private final EventOutbox outbox;
  private final AtomicReference<@Nullable List<CategoryRow>> categoryCache = new AtomicReference<>();
  /** Best-seller ranking per market: the start page is public and hot, the ranking changes slowly. */
  private final Map<UUID, RankedIds> bestsellerCache = new ConcurrentHashMap<>();

  private record RankedIds(Instant at, List<UUID> ids) {}

  public CatalogService(
      ListingRepository listings,
      CatalogProductRepository products,
      CategoryRepository categories,
      MarketService markets,
      PromotionService promotions,
      ListingFavorites favorites,
      PurchaseHistory purchases,
      LocaleService locales,
      Clock clock,
      DukkanProperties properties,
      EventOutbox outbox) {
    this.listings = listings;
    this.products = products;
    this.categories = categories;
    this.markets = markets;
    this.promotions = promotions;
    this.favorites = favorites;
    this.purchases = purchases;
    this.locales = locales;
    this.clock = clock;
    this.properties = properties;
    this.outbox = outbox;
  }

  // ------------------------------------------------------------------ customer reads

  /** Categories with products in an active market, with product counts (parents include children). */
  public List<CategoryView> marketCategories(UUID marketId, ContentLocale locale) {
    MarketInfo market = markets.activeInfo(marketId);
    Map<UUID, Integer> direct = listings.countByCategory(marketId);
    Map<UUID, Integer> total = new HashMap<>(direct);
    for (CategoryRow row : categoryRows()) {
      if (row.parentId() != null && direct.containsKey(row.id())) {
        total.merge(row.parentId(), direct.get(row.id()), Integer::sum);
      }
    }
    return categoryRows().stream()
        .filter(row -> total.getOrDefault(row.id(), 0) > 0)
        .map(row -> category(row, locale, market.primaryLocale(), total.get(row.id())))
        .toList();
  }

  /** The whole localised category tree (no counts), e.g. for merchants creating a first product. */
  public List<CategoryView> allCategories(ContentLocale locale) {
    return categoryRows().stream().map(row -> category(row, locale, null, null)).toList();
  }

  /** Localised categories by id (no counts), in sort order. */
  public List<CategoryView> categories(Collection<UUID> ids, ContentLocale locale, @Nullable String primaryLocale) {
    return categoryRows().stream()
        .filter(row -> ids.contains(row.id()))
        .map(row -> category(row, locale, primaryLocale, null))
        .toList();
  }

  /** Browse and search one active market. */
  public PageResponse<ProductSummary> searchMarket(
      UUID marketId, MarketProductQuery query, ContentLocale locale, @Nullable UUID userId) {
    MarketInfo market = markets.activeInfo(marketId);
    if (query.bestsellersOnly()) {
      List<ProductSummary> ranked = bestsellers(marketId, BESTSELLER_LIST_SIZE, locale, userId);
      int from = (int) Math.min(ranked.size(), (long) query.page() * query.size());
      int to = Math.min(ranked.size(), from + query.size());
      return PageResponse.of(ranked.subList(from, to), query.page(), query.size(), ranked.size());
    }
    String term = normalizedTerm(query.q());
    Set<UUID> promoted = query.promotedOnly() ? promotions.activeListingIds(marketId) : null;
    Instant newSince = query.newOnly() ? newSince() : null;
    IdPage page =
        listings.search(
            new ListingRepository.MarketSearch(
                marketId,
                term,
                query.categoryId(),
                promoted,
                newSince,
                query.organicOnly(),
                false,
                query.sort(),
                locale.chain(market.primaryLocale()),
                clock.instant()),
            query.page(),
            query.size());
    return PageResponse.of(summaries(page.ids(), locale, userId), query.page(), query.size(), page.total());
  }

  /** Days of orders that count for a market's best sellers. */
  public static final int BESTSELLER_DAYS = 30;

  /** Best sellers shown under "Alle". */
  static final int BESTSELLER_LIST_SIZE = 50;

  /** How long a market's best-seller ranking is reused. */
  public static final Duration BESTSELLER_CACHE = Duration.ofMinutes(5);

  /**
   * The market's available products sold most in the last {@link #BESTSELLER_DAYS} days (units,
   * counted orders only), most first.
   */
  public List<ProductSummary> bestsellers(UUID marketId, int limit, ContentLocale locale, @Nullable UUID userId) {
    Instant now = clock.instant();
    RankedIds cached = bestsellerCache.get(marketId);
    if (cached == null || cached.at().plus(BESTSELLER_CACHE).isBefore(now)) {
      // Some of the best sellers may be sold out right now; rank more than are shown.
      cached = new RankedIds(now, purchases.bestSellingListingIds(marketId, now.minus(Duration.ofDays(BESTSELLER_DAYS)), BESTSELLER_LIST_SIZE * 2));
      bestsellerCache.put(marketId, cached);
    }
    // Availability and prices are read fresh; only the ranking is reused.
    return summaries(cached.ids(), locale, userId).stream().filter(ProductSummary::available).limit(limit).toList();
  }

  /** Localised product names in one active market, best match first. */
  public List<String> suggestions(UUID marketId, String q, int limit, ContentLocale locale) {
    MarketInfo market = markets.activeInfo(marketId);
    String term = normalizedTerm(q);
    if (term == null) {
      return List.of();
    }
    return listings.suggest(marketId, term, locale.chain(market.primaryLocale()), limit);
  }

  /** Product details of a listing in an active market. */
  public ProductDetail product(UUID listingId, ContentLocale locale, @Nullable UUID userId) {
    ListingRow row = listings.findByIds(List.of(listingId)).get(listingId);
    if (row == null || row.deleted()) {
      throw productNotFound();
    }
    MarketInfo market = markets.findInfo(row.marketId()).filter(MarketInfo::isActive).orElseThrow(CatalogService::productNotFound);
    ProductSummary summary = summarize(List.of(row), locale, userId).get(listingId);
    MarketSummary marketSummary = markets.summary(market.id(), locale);
    String primary = market.primaryLocale();
    return new ProductDetail(
        Objects.requireNonNull(summary),
        row.categoryId(),
        locale.pick(row.texts().descriptions(), primary),
        locale.pick(row.texts().ingredients(), primary),
        row.allergens(),
        row.origin(),
        markets.cuisineNames(row.cuisines(), locale),
        row.barcode(),
        marketSummary);
  }

  /** Available offers matching {@code q} in active markets around a location. */
  public PageResponse<MarketOffer> searchNearby(
      String q, GeoPoint location, double radiusKm, String sort, int page, int size, ContentLocale locale, @Nullable UUID userId) {
    String term = normalizedTerm(q);
    List<MarketDistance> nearby = markets.activeWithin(location, radiusKm);
    if (term == null || nearby.isEmpty()) {
      return PageResponse.of(List.of(), page, size, 0);
    }
    Map<UUID, Double> distance = new HashMap<>();
    nearby.forEach(m -> distance.put(m.marketId(), m.distanceMeters()));
    List<Hit> hits = new ArrayList<>(listings.searchMarkets(distance.keySet(), term, locale.chain(null), NEARBY_CAP, clock.instant()));
    Comparator<Hit> relevance =
        Comparator.comparingInt(Hit::tier).thenComparingInt(Hit::nameLength).thenComparing(Hit::name);
    Comparator<Hit> byDistance = Comparator.comparing(h -> distance.get(h.marketId()));
    hits.sort(
        switch (sort) {
          case "price_asc" -> Comparator.comparingLong(Hit::priceCents).thenComparing(relevance);
          case "distance" -> byDistance.thenComparing(relevance);
          default -> relevance.thenComparing(byDistance);
        });
    PageResponse<Hit> slice = PageResponse.slice(hits, page, size);
    Map<UUID, ProductSummary> summaries = summarizeMap(slice.items().stream().map(Hit::listingId).toList(), locale, userId);
    Map<UUID, MarketSummary> marketSummaries =
        markets.summaries(slice.items().stream().map(Hit::marketId).toList(), location, locale);
    return slice.map(h -> new MarketOffer(summaries.get(h.listingId()), marketSummaries.get(h.marketId())));
  }

  /** Product summaries in the given order (deleted listings are skipped). */
  public List<ProductSummary> summaries(List<UUID> listingIds, ContentLocale locale, @Nullable UUID userId) {
    Map<UUID, ProductSummary> map = summarizeMap(listingIds, locale, userId);
    return listingIds.stream().map(map::get).filter(Objects::nonNull).toList();
  }

  /** Facts of listings for carts and orders (including deleted ones), keyed by listing id. */
  public Map<UUID, ListingInfo> listingInfos(Collection<UUID> listingIds, ContentLocale locale) {
    Map<UUID, ListingRow> rows = listings.findByIds(listingIds);
    Map<UUID, String> primary = primaryLocales(rows.values());
    Map<UUID, PromotionRule> rules = promotions.winningRules(rows.keySet());
    Map<UUID, ListingInfo> result = new LinkedHashMap<>();
    for (ListingRow row : rows.values()) {
      PromotionRule rule = row.deleted() ? null : rules.get(row.id());
      result.put(
          row.id(),
          new ListingInfo(
              row.id(),
              row.marketId(),
              row.catalogProductId(),
              row.categoryId(),
              name(row, locale, primary.get(row.marketId())),
              row.unitSize(),
              row.imageUrl(),
              row.iconKey(),
              row.priceCents(),
              rule,
              badge(rule, locale),
              row.available() && !row.deleted(),
              row.deleted(),
              row.maxOrderQuantity()));
    }
    return result;
  }

  /** Non-deleted listing ids of the market among {@code listingIds}. */
  public Set<UUID> activeListingIdsOfMarket(UUID marketId, Collection<UUID> listingIds) {
    return listings.findActiveIdsOfMarket(marketId, listingIds);
  }

  /** Listing ids created within the "new" window, newest first. */
  public List<UUID> newListingIds(UUID marketId, int limit) {
    return listings
        .search(
            new ListingRepository.MarketSearch(
                marketId, null, null, null, newSince(), false, true, "newest", List.of(ContentLocale.DEFAULT), clock.instant()),
            0,
            limit)
        .ids();
  }

  /** Listing ids of one category (incl. sub-categories), by name. */
  public List<UUID> categoryListingIds(UUID marketId, UUID categoryId, List<String> localeChain, int limit) {
    return listings
        .search(
            new ListingRepository.MarketSearch(
                marketId, null, categoryId, null, null, false, true, "name", localeChain, clock.instant()),
            0,
            limit)
        .ids();
  }

  public Map<UUID, Integer> categoryCounts(UUID marketId) {
    return listings.countByCategory(marketId);
  }

  // ------------------------------------------------------------------ merchant

  /** Shared catalog search; {@code alreadyListed} is computed for {@code marketContext} when given. */
  public PageResponse<CatalogProduct> sharedCatalog(
      @Nullable String q,
      @Nullable String cuisine,
      @Nullable UUID categoryId,
      @Nullable UUID marketContext,
      int page,
      int size,
      ContentLocale locale) {
    CatalogPage result =
        products.searchShared(normalizedTerm(q), cuisine, categoryId, marketContext, locale.chain(null), page, size);
    List<CatalogProduct> items =
        result.rows().stream()
            .map(
                row -> {
                  String name = locale.pick(row.names());
                  return new CatalogProduct(
                      row.id(),
                      name == null ? "" : name,
                      row.brand(),
                      row.unitSize(),
                      row.imageUrl(),
                      row.iconKey(),
                      row.categoryId(),
                      markets.cuisineNames(row.cuisines(), locale),
                      row.organic(),
                      row.barcode(),
                      row.alreadyListed());
                })
            .toList();
    return PageResponse.of(items, page, size, result.total());
  }

  /** Texts of a market-private product, or empty for shared catalog products and unknown ids. */
  public Optional<PrivateProductTexts> privateProductTexts(UUID catalogProductId) {
    return products
        .texts(catalogProductId)
        .filter(t -> t.ownerMarketId() != null)
        .map(t -> new PrivateProductTexts(t.id(), t.ownerMarketId(), markets.info(t.ownerMarketId()).primaryLocale(), Map.copyOf(t.names()), Map.copyOf(t.descriptions())));
  }

  /**
   * Adds machine translations for locales the product does not have yet (never overwrites a
   * merchant's text) and rebuilds the search text, so the product is found in those languages.
   */
  @Transactional
  public int addMachineTranslations(UUID catalogProductId, Map<String, String> names, Map<String, String> descriptions) {
    var texts = products.texts(catalogProductId).orElseThrow(() -> new NotFoundException("Catalog product not found."));
    int added = 0;
    for (Map.Entry<String, String> name : names.entrySet()) {
      String value = name.getValue().strip();
      if (value.isEmpty() || value.length() > 200 || texts.names().containsKey(name.getKey())) {
        continue;
      }
      String description = descriptions.get(name.getKey());
      if (products.insertMachineTranslation(
          catalogProductId, name.getKey(), value, description == null || description.isBlank() ? null : description.strip(), TextNormalizer.normalize(value))) {
        texts.names().put(name.getKey(), value);
        added++;
      }
    }
    if (added > 0) {
      products.updateSearchText(catalogProductId, SearchText.build(texts.names().values(), texts.brand(), texts.keywords()));
    }
    return added;
  }

  /** All non-deleted listings of a market (any status, available or not). */
  public PageResponse<Listing> merchantListings(UUID marketId, @Nullable String q, int page, int size, ContentLocale locale) {
    MarketInfo market = markets.info(marketId);
    IdPage ids = listings.merchantListings(marketId, normalizedTerm(q), locale.chain(market.primaryLocale()), page, size);
    Map<UUID, ListingRow> rows = listings.findByIds(ids.ids());
    Map<UUID, ProductSummary> summaries = summarize(List.copyOf(rows.values()), locale, null);
    List<Listing> items =
        ids.ids().stream()
            .filter(summaries::containsKey)
            .map(id -> listing(rows.get(id), summaries.get(id)))
            .toList();
    return PageResponse.of(items, page, size, ids.total());
  }

  @Transactional
  public Listing createListing(UUID marketId, ListingDraft draft, ContentLocale locale) {
    MarketInfo market = markets.info(marketId);
    if ((draft.catalogProductId() == null) == (draft.newProduct() == null)) {
      throw new InvalidRequestException("catalogProductId", "ExactlyOne", "give exactly one of catalogProductId or newProduct");
    }
    ListingOverrides overrides = draft.overrides().normalized();
    if (draft.newProduct() != null && !overrides.isEmpty()) {
      throw new InvalidRequestException("overrides", "CatalogProductOnly", "own details adjust a catalog product; a new product has them already");
    }
    Instant now = clock.instant();
    UUID catalogProductId;
    if (draft.catalogProductId() != null) {
      catalogProductId = draft.catalogProductId();
      UUID owner =
          products
              .findOwner(catalogProductId)
              .orElseThrow(() -> new NotFoundException("Catalog product not found."))
              .ownerMarketId();
      if (owner != null && !owner.equals(marketId)) {
        throw new NotFoundException("Catalog product not found.");
      }
      if (listings.findActiveListingId(marketId, catalogProductId).isPresent()) {
        throw new ConflictException(ErrorCodes.LISTING_EXISTS, "This market already lists the product.");
      }
    } else {
      NewProduct product = Objects.requireNonNull(draft.newProduct());
      validate(product, market);
      catalogProductId = UUID.randomUUID();
      products.insertPrivate(
          catalogProductId,
          marketId,
          product,
          SearchText.build(product.name().values(), product.brand(), product.keywords()),
          now);
      product.name().forEach((code, name) -> products.updateSearchName(catalogProductId, code, TextNormalizer.normalize(name)));
      // Machine translation fills the missing languages afterwards (B-38).
      outbox.append(PrivateProductCreated.TYPE, catalogProductId, new PrivateProductCreated(catalogProductId, marketId));
    }
    UUID listingId = UUID.randomUUID();
    int maxOrderQuantity = Objects.requireNonNullElse(draft.maxOrderQuantity(), market.ordering().defaultMaxOrderQuantity());
    listings.insert(listingId, marketId, catalogProductId, draft.priceCents(), draft.available(), maxOrderQuantity, now);
    if (!overrides.isEmpty()) {
      saveOverrides(listingId, overrides, now);
    }
    return listing(listingId, locale);
  }

  /**
   * Bulk import (B-31). Each row is matched to a product (barcode first, then the market's own
   * product with the same name and pack size) and becomes a new private product, a listing of an
   * existing product or an update of the market's listing. All or nothing: rows are written only
   * when no row has an error and {@code dryRun} is false.
   */
  @Transactional
  public ListingImport.Result importListings(UUID marketId, List<ListingImport.Row> rows, boolean dryRun) {
    MarketInfo market = markets.info(marketId);
    String primary = market.primaryLocale();
    Map<String, UUID> categoryIds = new HashMap<>();
    categoryRows().forEach(c -> categoryIds.put(c.slug(), c.id()));
    record Planned(ListingImport.Row row, ListingImport.Action action, @Nullable UUID productId, @Nullable UUID listingId, @Nullable NewProduct product) {}
    List<Planned> plans = new ArrayList<>();
    List<ListingImport.RowResult> results = new ArrayList<>();
    Map<String, Integer> seen = new HashMap<>();
    for (ListingImport.Row row : rows) {
      List<ListingImport.RowError> errors = new ArrayList<>(row.errors());
      String name = row.names().get(primary);
      UUID productId = null;
      if (row.barcode() != null) {
        productId = products.findByBarcode(row.barcode(), marketId).orElse(null);
      }
      if (productId == null && name != null && row.unitSize() != null) {
        productId = products.findPrivateByName(marketId, primary, TextNormalizer.normalize(name), row.unitSize()).orElse(null);
      }
      UUID listingId = productId == null ? null : listings.findActiveListingId(marketId, productId).orElse(null);
      ListingImport.Action action = productId == null ? ListingImport.Action.CREATE : listingId == null ? ListingImport.Action.LIST : ListingImport.Action.UPDATE;
      NewProduct product = null;
      if (action != ListingImport.Action.UPDATE && row.priceCents() == null && errors.stream().noneMatch(e -> e.field().equals("price"))) {
        errors.add(new ListingImport.RowError("price", "Required", "a price is required for a new listing"));
      }
      if (action == ListingImport.Action.CREATE) {
        if (name == null) {
          errors.add(new ListingImport.RowError("name_" + primary, "PrimaryLocaleRequired", "the name in the market's primary language " + primary + " is required"));
        }
        if (row.unitSize() == null) {
          errors.add(new ListingImport.RowError("unit_size", "Required", "the pack size is required for a new product, e.g. 500 g"));
        }
        UUID categoryId = row.categorySlug() == null ? null : categoryIds.get(row.categorySlug());
        if (categoryId == null) {
          errors.add(new ListingImport.RowError("category", row.categorySlug() == null ? "Required" : "UnknownCode", "a known category is required, e.g. " + String.join(", ", categoryIds.keySet().stream().sorted().limit(5).toList())));
        }
        if (errors.isEmpty()) {
          product =
              new NewProduct(
                  row.names(), row.descriptions(), Map.of(), row.keywords(), row.brand(), Objects.requireNonNull(row.unitSize()), null, null, categoryId,
                  row.cuisineCodes(), row.organic(), row.allergens(), null, row.barcode());
          try {
            validate(product, market);
          } catch (InvalidRequestException e) {
            errors.add(new ListingImport.RowError(importField(e.field()), e.violation(), e.getMessage()));
          }
        }
      }
      String key = productId != null ? productId.toString() : name == null ? null : TextNormalizer.normalize(name) + "|" + (row.unitSize() == null ? "" : row.unitSize().toLowerCase(Locale.ROOT));
      if (key != null) {
        Integer first = seen.putIfAbsent(key, row.line());
        if (first != null) {
          errors.add(new ListingImport.RowError(row.barcode() != null ? "barcode" : "name_" + primary, "Duplicate", "the same product as line " + first));
        }
      }
      String label = name != null ? name : row.names().values().stream().findFirst().orElseGet(row::barcode);
      if (errors.isEmpty()) {
        plans.add(new Planned(row, action, productId, listingId, product));
        results.add(new ListingImport.RowResult(row.line(), action, label, listingId, List.of()));
      } else {
        results.add(new ListingImport.RowResult(row.line(), ListingImport.Action.ERROR, label, null, List.copyOf(errors)));
      }
    }
    int failed = (int) results.stream().filter(r -> r.action() == ListingImport.Action.ERROR).count();
    boolean apply = !dryRun && failed == 0 && !plans.isEmpty();
    if (apply) {
      results.clear();
      for (Planned plan : plans) {
        ListingImport.Row row = plan.row();
        UUID listingId =
            switch (plan.action()) {
              case UPDATE -> {
                updateListing(marketId, Objects.requireNonNull(plan.listingId()), new ListingUpdate(row.priceCents(), row.available(), row.maxOrderQuantity(), false, null), ContentLocale.of(market.primaryLocale()));
                yield plan.listingId();
              }
              case LIST, CREATE ->
                  createListing(
                          marketId,
                          new ListingDraft(
                              plan.productId(),
                              plan.product(),
                              Objects.requireNonNull(row.priceCents()),
                              row.available() == null || row.available(),
                              row.maxOrderQuantity()),
                          ContentLocale.of(market.primaryLocale()))
                      .summary()
                      .id();
              case ERROR -> throw new IllegalStateException("planned rows have no errors");
            };
        String label = row.names().getOrDefault(market.primaryLocale(), row.names().values().stream().findFirst().orElseGet(row::barcode));
        results.add(new ListingImport.RowResult(row.line(), plan.action(), label, listingId, List.of()));
      }
    }
    return new ListingImport.Result(
        dryRun,
        apply,
        count(results, ListingImport.Action.CREATE),
        count(results, ListingImport.Action.LIST),
        count(results, ListingImport.Action.UPDATE),
        failed,
        List.copyOf(results));
  }

  /**
   * Imports products into the shared catalog (platform admins), e.g. the start catalog before
   * launch. Every row needs a German name ({@link #SHARED_CATALOG_LOCALE}), a pack size and a
   * category; products already in the shared catalog (same barcode, or same German name and pack
   * size) are skipped. All or nothing, like {@link #importListings}.
   */
  @Transactional
  public CatalogImport.Result importShared(List<ListingImport.Row> rows, boolean dryRun) {
    Map<String, UUID> categoryIds = new HashMap<>();
    categoryRows().forEach(c -> categoryIds.put(c.slug(), c.id()));
    record Planned(ListingImport.Row row, NewProduct product) {}
    List<Planned> plans = new ArrayList<>();
    List<CatalogImport.RowResult> results = new ArrayList<>();
    Map<String, Integer> seen = new HashMap<>();
    for (ListingImport.Row row : rows) {
      List<ListingImport.RowError> errors = new ArrayList<>(row.errors());
      String name = row.names().get(SHARED_CATALOG_LOCALE);
      String label = name != null ? name : row.names().values().stream().findFirst().orElseGet(row::barcode);
      if (name == null) {
        errors.add(new ListingImport.RowError("name_" + SHARED_CATALOG_LOCALE, "PrimaryLocaleRequired", "the shared catalog needs a German name"));
      }
      if (row.unitSize() == null) {
        errors.add(new ListingImport.RowError("unit_size", "Required", "the pack size is required, e.g. 500 g"));
      }
      UUID categoryId = row.categorySlug() == null ? null : categoryIds.get(row.categorySlug());
      if (categoryId == null) {
        errors.add(new ListingImport.RowError("category", row.categorySlug() == null ? "Required" : "UnknownCode", "a known category is required, e.g. " + String.join(", ", categoryIds.keySet().stream().sorted().limit(5).toList())));
      }
      if (!errors.isEmpty()) {
        results.add(new CatalogImport.RowResult(row.line(), CatalogImport.Action.ERROR, label, null, List.copyOf(errors)));
        continue;
      }
      String unitSize = Objects.requireNonNull(row.unitSize());
      String searchName = TextNormalizer.normalize(Objects.requireNonNull(name));
      UUID existing = products.findShared(row.barcode(), SHARED_CATALOG_LOCALE, searchName, unitSize).orElse(null);
      if (existing != null) {
        results.add(new CatalogImport.RowResult(row.line(), CatalogImport.Action.EXISTS, label, existing, List.of()));
        continue;
      }
      NewProduct product =
          new NewProduct(
              row.names(), row.descriptions(), Map.of(), row.keywords(), row.brand(), unitSize, null, null, Objects.requireNonNull(categoryId),
              row.cuisineCodes(), row.organic(), row.allergens(), null, row.barcode());
      try {
        validate(product, SHARED_CATALOG_LOCALE);
      } catch (InvalidRequestException e) {
        errors.add(new ListingImport.RowError(importField(e.field()), e.violation(), e.getMessage()));
      }
      Integer first = seen.putIfAbsent(searchName + "|" + unitSize.toLowerCase(Locale.ROOT), row.line());
      if (first != null) {
        errors.add(new ListingImport.RowError("name_" + SHARED_CATALOG_LOCALE, "Duplicate", "the same product as line " + first));
      }
      if (errors.isEmpty()) {
        plans.add(new Planned(row, product));
        results.add(new CatalogImport.RowResult(row.line(), CatalogImport.Action.CREATE, label, null, List.of()));
      } else {
        results.add(new CatalogImport.RowResult(row.line(), CatalogImport.Action.ERROR, label, null, List.copyOf(errors)));
      }
    }
    int failed = (int) results.stream().filter(r -> r.action() == CatalogImport.Action.ERROR).count();
    int existing = (int) results.stream().filter(r -> r.action() == CatalogImport.Action.EXISTS).count();
    boolean apply = !dryRun && failed == 0 && !plans.isEmpty();
    if (apply) {
      Instant now = clock.instant();
      Map<Integer, UUID> created = new HashMap<>();
      for (Planned plan : plans) {
        UUID id = UUID.randomUUID();
        NewProduct product = plan.product();
        products.insertShared(id, product, SearchText.build(product.name().values(), product.brand(), product.keywords()), now);
        product.name().forEach((code, text) -> products.updateSearchName(id, code, TextNormalizer.normalize(text)));
        created.put(plan.row().line(), id);
      }
      results.replaceAll(r -> r.action() == CatalogImport.Action.CREATE ? new CatalogImport.RowResult(r.line(), r.action(), r.name(), created.get(r.line()), List.of()) : r);
    }
    return new CatalogImport.Result(dryRun, apply, plans.size(), existing, failed, List.copyOf(results));
  }

  private static int count(List<ListingImport.RowResult> results, ListingImport.Action action) {
    return (int) results.stream().filter(r -> r.action() == action).count();
  }

  /** Maps a validation field of {@code NewCatalogProduct} to the CSV column. */
  private static String importField(String field) {
    return switch (field) {
      case "newProduct.categoryId" -> "category";
      case "newProduct.cuisineCodes" -> "cuisines";
      case "newProduct.name" -> "name";
      default -> field.startsWith("newProduct.") ? field.substring("newProduct.".length()) : field;
    };
  }

  @Transactional
  public Listing updateListing(UUID marketId, UUID listingId, ListingUpdate update, ContentLocale locale) {
    ListingRow row = ownedListing(marketId, listingId);
    Instant now = clock.instant();
    if (update.priceCents() != null && update.priceCents() != row.priceCents()) {
      listings.updatePrice(listingId, update.priceCents(), now);
    }
    if (update.available() != null || update.maxOrderQuantity() != null) {
      listings.updateFlags(listingId, update.available(), update.maxOrderQuantity(), now);
    }
    if (update.overrides() != null) {
      ListingOverrides overrides = update.overrides().normalized();
      if (row.ownerMarketId() != null && !overrides.isEmpty()) {
        throw new InvalidRequestException("overrides", "CatalogProductOnly", "own details adjust a catalog product; a new product has them already");
      }
      saveOverrides(listingId, overrides, now);
    }
    if (update.product() != null) {
      if (row.ownerMarketId() == null) {
        throw new InvalidRequestException("product", "PrivateProductOnly", "a shared catalog product is adjusted with overrides");
      }
      updatePrivateProduct(marketId, row.catalogProductId(), update.product(), now);
    }
    if (update.changeImage()) {
      if (row.ownerMarketId() != null) {
        // A private product belongs to this market: its own image is the product image.
        products.updateImage(row.catalogProductId(), update.imageUrl(), now);
        listings.updateImage(listingId, null, now);
      } else {
        // Shared catalog product: the photo overrides the catalog image for this listing only.
        listings.updateImage(listingId, update.imageUrl(), now);
      }
    }
    return listing(listingId, locale);
  }

  /** Replaces the details of the market's own product and lets the translator fill missing languages again. */
  private void updatePrivateProduct(UUID marketId, UUID productId, NewProduct product, Instant now) {
    validate(product, markets.info(marketId));
    String searchText = SearchText.build(product.name().values(), product.brand(), product.keywords());
    if (!products.updatePrivate(productId, marketId, product, searchText, now)) {
      throw productNotFound();
    }
    product.name().forEach((code, name) -> products.updateSearchName(productId, code, TextNormalizer.normalize(name)));
    // Languages not sent may have been kept: the search text covers every name the product has now.
    products
        .texts(productId)
        .ifPresent(t -> products.updateSearchText(productId, SearchText.build(t.names().values(), product.brand(), product.keywords())));
    outbox.append(PrivateProductCreated.TYPE, productId, new PrivateProductCreated(productId, marketId));
  }

  /** A listing of the market with every detail of its product, for the merchant's edit form. */
  @Transactional(readOnly = true)
  public ListingDetails listingDetails(UUID marketId, UUID listingId, ContentLocale locale) {
    ListingRow row = ownedListing(marketId, listingId);
    NewProduct product = products.details(row.catalogProductId()).orElseThrow(CatalogService::productNotFound);
    return new ListingDetails(listing(listingId, locale), product);
  }

  @Transactional
  public void deleteListing(UUID marketId, UUID listingId) {
    if (listings.softDelete(marketId, listingId, clock.instant()) == 0) {
      throw productNotFound();
    }
  }

  private Listing listing(UUID listingId, ContentLocale locale) {
    ListingRow row = Objects.requireNonNull(listings.findByIds(List.of(listingId)).get(listingId));
    return listing(row, Objects.requireNonNull(summarize(List.of(row), locale, null).get(listingId)));
  }

  private static Listing listing(ListingRow row, ProductSummary summary) {
    ListingOverrides overrides = row.overrides();
    boolean restricted = RestrictedGoods.suspected(summary.name(), summary.brand(), overrides.name(), overrides.brand());
    return new Listing(summary, row.ownerMarketId() != null, overrides.isEmpty() ? null : overrides, restricted);
  }

  /** The own name and brand are searchable in this market like catalog names. */
  private void saveOverrides(UUID listingId, ListingOverrides overrides, Instant now) {
    String searchText =
        overrides.name() == null && overrides.brand() == null
            ? null
            : SearchText.build(overrides.name() == null ? List.of() : List.of(overrides.name()), overrides.brand(), List.of());
    listings.updateOverrides(listingId, overrides, searchText, now);
  }

  private ListingRow ownedListing(UUID marketId, UUID listingId) {
    ListingRow row = listings.findByIds(List.of(listingId)).get(listingId);
    if (row == null || row.deleted() || !row.marketId().equals(marketId)) {
      throw productNotFound();
    }
    return row;
  }

  private void validate(NewProduct p, MarketInfo market) {
    validate(p, market.primaryLocale());
  }

  private void validate(NewProduct p, String primaryLocale) {
    if (p.name().isEmpty() || !p.name().containsKey(primaryLocale)) {
      throw new InvalidRequestException(
          "newProduct.name", "PrimaryLocaleRequired", "must contain the market's primary locale " + primaryLocale);
    }
    for (Map<String, String> texts : List.of(p.name(), p.description(), p.tagline())) {
      for (String code : texts.keySet()) {
        if (!locales.isEnabled(code)) {
          throw new InvalidRequestException("newProduct", "SupportedLocale", "contains a locale that is not enabled: " + code);
        }
      }
    }
    if (p.name().values().stream().anyMatch(n -> n == null || n.isBlank() || n.length() > 150)) {
      throw new InvalidRequestException("newProduct.name", "Size", "names must have 1 to 150 characters");
    }
    if (categoryRows().stream().noneMatch(c -> c.id().equals(p.categoryId()))) {
      throw new InvalidRequestException("newProduct.categoryId", "UnknownCode", "is not a known category");
    }
    for (String cuisine : p.cuisineCodes()) {
      if (!markets.isCuisine(cuisine)) {
        throw new InvalidRequestException("newProduct.cuisineCodes", "UnknownCode", "contains an unknown cuisine: " + cuisine);
      }
    }
    if ((p.netQuantity() == null) != (p.netUnit() == null)) {
      throw new InvalidRequestException("newProduct.netUnit", "NetQuantity", "netQuantity and netUnit must be given together");
    }
  }

  // ------------------------------------------------------------------ mapping

  private Map<UUID, ProductSummary> summarizeMap(List<UUID> listingIds, ContentLocale locale, @Nullable UUID userId) {
    Map<UUID, ListingRow> rows = listings.findByIds(listingIds);
    return summarize(rows.values().stream().filter(r -> !r.deleted()).toList(), locale, userId);
  }

  private Map<UUID, ProductSummary> summarize(List<ListingRow> rows, ContentLocale locale, @Nullable UUID userId) {
    if (rows.isEmpty()) {
      return Map.of();
    }
    List<UUID> ids = rows.stream().map(ListingRow::id).toList();
    Map<UUID, PromotionRule> rules = promotions.winningRules(ids);
    Set<UUID> favoriteIds = userId == null ? Set.of() : favorites.favoriteListingIds(userId, ids);
    Map<UUID, String> primary = primaryLocales(rows);
    Instant newSince = newSince();
    Map<UUID, ProductSummary> result = new LinkedHashMap<>();
    for (ListingRow row : rows) {
      String primaryLocale = primary.get(row.marketId());
      PromotionRule rule = rules.get(row.id());
      Long promotional = null;
      if (rule instanceof PromotionRule.PercentOff || rule instanceof PromotionRule.FixedPrice) {
        long reduced = PricingEngine.price(List.of(new PricingEngine.Line(row.id(), row.priceCents(), 1, rule)), 0).totalCents();
        promotional = reduced < row.priceCents() ? reduced : null;
      }
      long effective = promotional == null ? row.priceCents() : promotional;
      UnitPrice unitPrice =
          ComparisonPrice.of(effective, row.netQuantity(), row.netUnit())
              .map(c -> new UnitPrice(Money.eur(c.amountCents()), c.unit()))
              .orElse(null);
      result.put(
          row.id(),
          new ProductSummary(
              row.id(),
              row.marketId(),
              row.catalogProductId(),
              name(row, locale, primaryLocale),
              row.brand(),
              locale.pick(row.texts().taglines(), primaryLocale),
              row.unitSize(),
              row.imageUrl(),
              row.iconKey(),
              Money.eur(row.priceCents()),
              promotional == null ? null : Money.eur(promotional),
              unitPrice,
              badge(rule, locale),
              row.available() && !row.deleted(),
              row.organic(),
              !row.createdAt().isBefore(newSince),
              row.maxOrderQuantity(),
              userId == null ? null : favoriteIds.contains(row.id())));
    }
    return result;
  }

  private @Nullable PromotionBadge badge(@Nullable PromotionRule rule, ContentLocale locale) {
    return rule == null ? null : new PromotionBadge(rule.promotionId(), promotions.badgeLabel(rule, locale), rule.type());
  }

  private static String name(ListingRow row, ContentLocale locale, @Nullable String primaryLocale) {
    String name = locale.pick(row.texts().names(), primaryLocale);
    return name == null ? "" : name;
  }

  private Map<UUID, String> primaryLocales(Collection<ListingRow> rows) {
    Map<UUID, String> primary = new HashMap<>();
    for (UUID marketId : rows.stream().map(ListingRow::marketId).distinct().toList()) {
      markets.findInfo(marketId).ifPresent(m -> primary.put(marketId, m.primaryLocale()));
    }
    return primary;
  }

  private Instant newSince() {
    return clock.instant().minus(Duration.ofDays(properties.catalog().newListingDays()));
  }

  private static @Nullable String normalizedTerm(@Nullable String q) {
    String term = TextNormalizer.normalize(q);
    return term.isEmpty() ? null : term;
  }

  private List<CategoryRow> categoryRows() {
    List<CategoryRow> rows = categoryCache.get();
    if (rows == null) {
      rows = categories.findAll();
      categoryCache.set(rows);
    }
    return rows;
  }

  private static CategoryView category(
      CategoryRow row, ContentLocale locale, @Nullable String primaryLocale, @Nullable Integer count) {
    String name = locale.pick(row.names(), primaryLocale);
    return new CategoryView(
        row.id(), row.parentId(), row.slug(), name == null ? row.slug() : name, row.iconKey(), row.imageUrl(), row.sortOrder(), count);
  }

  private static NotFoundException productNotFound() {
    return new NotFoundException("Product not found.");
  }
}
