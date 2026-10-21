package de.dukkan.market.application;

import de.dukkan.geo.application.GeoMath;
import de.dukkan.i18n.application.ContentLocale;
import de.dukkan.i18n.application.LocaleService;
import de.dukkan.i18n.application.TextNormalizer;
import de.dukkan.market.domain.OpeningSchedule;
import de.dukkan.market.infrastructure.MarketRepository;
import de.dukkan.market.infrastructure.MarketRepository.BannerRow;
import de.dukkan.market.infrastructure.MarketRepository.MarketRow;
import de.dukkan.promotion.application.PromotionService;
import de.dukkan.shared.api.CodeName;
import de.dukkan.shared.api.GeoPoint;
import de.dukkan.shared.api.Money;
import de.dukkan.shared.api.PageResponse;
import de.dukkan.shared.config.DukkanProperties;
import de.dukkan.shared.error.InvalidRequestException;
import de.dukkan.shared.error.NotFoundException;
import de.dukkan.shared.time.DukkanTime;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Markets: discovery, profiles, opening state, banners, reference codes and ownership. */
@Service
@Transactional(readOnly = true)
public class MarketService {

  public static final String ACTIVE = "ACTIVE";
  public static final String PENDING_REVIEW = "PENDING_REVIEW";

  private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
  private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9]+");

  private record CodeTables(
      List<Map.Entry<String, @Nullable String>> types,
      Map<String, Map<String, String>> typeNames,
      List<Map.Entry<String, @Nullable String>> cuisines,
      Map<String, Map<String, String>> cuisineNames) {}

  private final MarketRepository markets;
  private final PromotionService promotions;
  private final LocaleService locales;
  private final Clock clock;
  private final DukkanProperties properties;
  private final AtomicReference<@Nullable CodeTables> codeTables = new AtomicReference<>();

  public MarketService(
      MarketRepository markets,
      PromotionService promotions,
      LocaleService locales,
      Clock clock,
      DukkanProperties properties) {
    this.markets = markets;
    this.promotions = promotions;
    this.locales = locales;
    this.clock = clock;
    this.properties = properties;
  }

  // ------------------------------------------------------------------ discovery

  /** Active markets near a location with filters, sort and paging. */
  public PageResponse<MarketSummary> search(MarketQuery query, ContentLocale locale) {
    Set<UUID> withOffers = query.hasOffers() ? promotions.marketsWithActivePromotions() : null;
    List<Map.Entry<UUID, Double>> candidates =
        markets.searchActive(
            new MarketRepository.Search(
                query.lat(),
                query.lng(),
                query.radiusKm() * 1000,
                query.types(),
                query.cuisines(),
                query.fulfilment(),
                withOffers,
                query.sort()));
    Map<UUID, MarketRow> rows = markets.findByIds(candidates.stream().map(Map.Entry::getKey).toList());
    Instant now = clock.instant();
    String name = query.q() == null || query.q().isBlank() ? null : TextNormalizer.normalize(query.q());
    List<MarketRow> matching = new ArrayList<>();
    for (Map.Entry<UUID, Double> candidate : candidates) {
      MarketRow row = rows.get(candidate.getKey());
      if (row == null
          || (query.openNow() && !schedule(row).isOpen(now))
          || (name != null && !TextNormalizer.normalize(row.name()).contains(name))) {
        continue;
      }
      matching.add(row);
    }
    PageResponse<MarketRow> page = PageResponse.slice(matching, query.page(), query.size());
    Map<UUID, Integer> counts = promotions.activeCounts(page.items().stream().map(MarketRow::id).toList());
    GeoPoint ref = GeoPoint.of(query.lat(), query.lng());
    return page.map(row -> summary(row, ref, counts.getOrDefault(row.id(), 0), locale, now));
  }

  /** Ids and distances of active markets within the radius, nearest first. */
  public List<MarketDistance> activeWithin(GeoPoint location, double radiusKm) {
    return markets
        .searchActive(
            new MarketRepository.Search(
                location.lat(), location.lng(), radiusKm * 1000, List.of(), List.of(), null, null, "distance"))
        .stream()
        .map(e -> new MarketDistance(e.getKey(), e.getValue()))
        .toList();
  }

  /** Public market details; {@code NOT_FOUND} unless the market is ACTIVE. */
  public MarketDetail detail(UUID marketId, @Nullable GeoPoint reference, ContentLocale locale) {
    MarketRow row = activeRow(marketId);
    return detail(row, reference, locale);
  }

  /** Summaries of the given markets (any status), keyed by id. */
  public Map<UUID, MarketSummary> summaries(Collection<UUID> marketIds, @Nullable GeoPoint reference, ContentLocale locale) {
    Map<UUID, MarketRow> rows = markets.findByIds(marketIds);
    Map<UUID, Integer> counts = promotions.activeCounts(rows.keySet());
    Instant now = clock.instant();
    Map<UUID, MarketSummary> result = new LinkedHashMap<>();
    rows.values().forEach(row -> result.put(row.id(), summary(row, reference, counts.getOrDefault(row.id(), 0), locale, now)));
    return result;
  }

  /** Summary of one market (any status); {@code NOT_FOUND} if it does not exist. */
  public MarketSummary summary(UUID marketId, ContentLocale locale) {
    MarketSummary summary = summaries(List.of(marketId), null, locale).get(marketId);
    if (summary == null) {
      throw notFound();
    }
    return summary;
  }

  /** Facts of a market of any status; {@code NOT_FOUND} if it does not exist. */
  public MarketInfo info(UUID marketId) {
    return info(row(marketId));
  }

  /** Facts of several markets of any status in one query; unknown ids are absent. */
  public Map<UUID, MarketInfo> infos(Collection<UUID> marketIds) {
    if (marketIds.isEmpty()) {
      return Map.of();
    }
    Map<UUID, MarketInfo> result = new HashMap<>();
    markets.findByIds(List.copyOf(new HashSet<>(marketIds))).forEach((id, row) -> result.put(id, info(row)));
    return result;
  }

  public Optional<MarketInfo> findInfo(UUID marketId) {
    return Optional.ofNullable(markets.findByIds(List.of(marketId)).get(marketId)).map(MarketService::info);
  }

  /** Facts of an ACTIVE market; {@code NOT_FOUND} otherwise (inactive markets are invisible to customers). */
  public MarketInfo activeInfo(UUID marketId) {
    return info(activeRow(marketId));
  }

  public List<Banner> banners(UUID marketId, ContentLocale locale) {
    String primary = activeRow(marketId).primaryLocale();
    List<Banner> banners = new ArrayList<>();
    for (BannerRow b : markets.findActiveBanners(marketId)) {
      String title = locale.pick(b.titles(), primary);
      banners.add(
          new Banner(
              b.id(),
              locale.pick(b.overlines(), primary),
              title == null ? "" : title,
              b.imageUrl(),
              b.tint(),
              new Banner.Target(b.targetType(), b.targetId())));
    }
    return banners;
  }

  // ------------------------------------------------------------------ reference codes

  public List<CodeName> marketTypes(ContentLocale locale) {
    CodeTables tables = codeTables();
    return tables.types().stream().map(e -> codeName(e.getKey(), e.getValue(), tables.typeNames(), locale)).toList();
  }

  public List<CodeName> cuisines(ContentLocale locale) {
    CodeTables tables = codeTables();
    return tables.cuisines().stream().map(e -> codeName(e.getKey(), e.getValue(), tables.cuisineNames(), locale)).toList();
  }

  /** Localised cuisines for the given codes, in reference order. */
  public List<CodeName> cuisineNames(Collection<String> codes, ContentLocale locale) {
    return cuisines(locale).stream().filter(c -> codes.contains(c.code())).toList();
  }

  public boolean isCuisine(String code) {
    return codeTables().cuisines().stream().anyMatch(e -> e.getKey().equals(code));
  }

  private boolean isMarketType(String code) {
    return codeTables().types().stream().anyMatch(e -> e.getKey().equals(code));
  }

  private static CodeName codeName(
      String code, @Nullable String iconKey, Map<String, Map<String, String>> names, ContentLocale locale) {
    String name = locale.pick(names.getOrDefault(code, Map.of()));
    return new CodeName(code, name == null ? code : name, iconKey);
  }

  private CodeTables codeTables() {
    CodeTables tables = codeTables.get();
    if (tables == null) {
      tables =
          new CodeTables(
              markets.codes("market_type"),
              markets.codeTranslations("market_type"),
              markets.codes("cuisine"),
              markets.codeTranslations("cuisine"));
      codeTables.set(tables);
    }
    return tables;
  }

  // ------------------------------------------------------------------ merchant

  public boolean isOwner(UUID marketId, UUID userId) {
    return markets.isOwner(marketId, userId);
  }

  public List<UUID> ownedMarketIds(UUID userId) {
    return markets.findOwnedIds(userId);
  }

  public List<MerchantMarket> owned(UUID userId, ContentLocale locale) {
    Map<UUID, MarketRow> rows = markets.findByIds(markets.findOwnedIds(userId));
    return rows.values().stream()
        .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
        .map(row -> new MerchantMarket(detail(row, null, locale), row.status()))
        .toList();
  }

  public MerchantMarket merchantView(UUID marketId, ContentLocale locale) {
    MarketRow row = row(marketId);
    return new MerchantMarket(detail(row, null, locale), row.status());
  }

  /** Admin view: markets of a status (all when null), newest first. */
  public List<MerchantMarket> listForAdmin(@Nullable String status, ContentLocale locale) {
    List<UUID> ids = markets.findIdsByStatus(status);
    Map<UUID, MarketRow> rows = markets.findByIds(ids);
    return ids.stream().map(rows::get).filter(Objects::nonNull).map(row -> new MerchantMarket(detail(row, null, locale), row.status())).toList();
  }

  /** Admin: approve (ACTIVE), suspend (SUSPENDED) or send back to review; {@code NOT_FOUND} if missing. */
  @Transactional
  public MerchantMarket setStatus(UUID marketId, String status, ContentLocale locale) {
    if (!markets.updateStatus(marketId, status, clock.instant())) {
      throw new NotFoundException("Market not found.");
    }
    return merchantView(marketId, locale);
  }

  @Transactional
  public MerchantMarket create(UUID ownerId, MarketProfile profile, ContentLocale locale) {
    validate(profile);
    UUID id = UUID.randomUUID();
    String status = properties.features().merchantAutoApprove() ? ACTIVE : PENDING_REVIEW;
    Instant now = clock.instant();
    markets.insert(id, uniqueSlug(profile.name()), status, profile, now);
    markets.addOwner(id, ownerId, now);
    return merchantView(id, locale);
  }

  @Transactional
  public MerchantMarket update(UUID marketId, MarketProfile profile, ContentLocale locale) {
    row(marketId);
    validate(profile);
    markets.update(marketId, profile, clock.instant());
    return merchantView(marketId, locale);
  }

  @Transactional
  public void assignOwner(UUID marketId, UUID userId) {
    row(marketId);
    markets.addOwner(marketId, userId, clock.instant());
  }

  public Optional<UUID> findIdBySlug(String slug) {
    return markets.findIdBySlug(slug);
  }

  private void validate(MarketProfile p) {
    if (!isMarketType(p.typeCode())) {
      throw new InvalidRequestException("typeCode", "UnknownCode", "is not a known market type");
    }
    for (String cuisine : p.cuisineCodes()) {
      if (!isCuisine(cuisine)) {
        throw new InvalidRequestException("cuisineCodes", "UnknownCode", "contains an unknown cuisine: " + cuisine);
      }
    }
    if (!locales.isEnabled(p.primaryLocale())) {
      throw new InvalidRequestException("primaryLocale", "SupportedLocale", "must be an enabled locale");
    }
    for (String code : p.description().keySet()) {
      if (!locales.isEnabled(code)) {
        throw new InvalidRequestException("description", "SupportedLocale", "contains a locale that is not enabled: " + code);
      }
    }
    FulfilmentOptions f = p.fulfilment();
    if (!f.delivery() && !f.pickup()) {
      throw new InvalidRequestException("fulfilment", "FulfilmentRequired", "offer delivery, pickup or both");
    }
    if (f.delivery() && (f.deliveryRadiusKm() == null || f.deliveryFee() == null)) {
      throw new InvalidRequestException(
          "fulfilment.deliveryRadiusKm", "NotNull", "deliveryRadiusKm and deliveryFee are required for delivery");
    }
    for (Money money : new Money[] {f.deliveryFee(), f.freeDeliveryFrom(), f.minimumOrderValue()}) {
      if (money != null && !money.isEuro()) {
        throw new InvalidRequestException("fulfilment", "Currency", "amounts must be in EUR");
      }
    }
    for (int i = 0; i < p.openingHours().size(); i++) {
      OpeningHours h = p.openingHours().get(i);
      try {
        if (!LocalTime.parse(h.closesAt()).isAfter(LocalTime.parse(h.opensAt()))) {
          throw new InvalidRequestException(
              "openingHours[" + i + "].closesAt", "AfterOpensAt", "must be after opensAt on the same day");
        }
      } catch (DateTimeParseException e) {
        throw new InvalidRequestException("openingHours[" + i + "]", "Time", "must be a valid time HH:mm");
      }
    }
  }

  private String uniqueSlug(String name) {
    String base = NON_SLUG.matcher(TextNormalizer.normalize(name)).replaceAll("-").replaceAll("(^-+|-+$)", "");
    if (base.isEmpty()) {
      base = "market";
    }
    base = base.length() > 80 ? base.substring(0, 80) : base;
    String slug = base;
    for (int i = 2; markets.slugExists(slug); i++) {
      slug = base + "-" + i;
    }
    return slug;
  }

  // ------------------------------------------------------------------ mapping

  private MarketRow row(UUID marketId) {
    MarketRow row = markets.findByIds(List.of(marketId)).get(marketId);
    if (row == null) {
      throw notFound();
    }
    return row;
  }

  private MarketRow activeRow(UUID marketId) {
    MarketRow row = row(marketId);
    if (!ACTIVE.equals(row.status())) {
      throw notFound();
    }
    return row;
  }

  private static NotFoundException notFound() {
    return new NotFoundException("Market not found.");
  }

  private MarketDetail detail(MarketRow row, @Nullable GeoPoint reference, ContentLocale locale) {
    int count = promotions.activeCounts(List.of(row.id())).getOrDefault(row.id(), 0);
    MarketSummary summary = summary(row, reference, count, locale, clock.instant());
    List<OpeningHours> hours =
        row.hours().stream()
            .map(h -> new OpeningHours(h.dayOfWeek(), HH_MM.format(h.opensAt()), HH_MM.format(h.closesAt())))
            .toList();
    return new MarketDetail(
        summary,
        locale.pick(row.descriptions(), row.primaryLocale()),
        row.phone(),
        hours,
        row.primaryLocale(),
        new MarketDetail.Imprint(row.legalName(), row.addressLine(), row.registerInfo()));
  }

  private MarketSummary summary(
      MarketRow row, @Nullable GeoPoint reference, int promotionCount, ContentLocale locale, Instant now) {
    OpeningSchedule schedule = schedule(row);
    Integer distance = null;
    Boolean deliversTo = null;
    if (reference != null) {
      double meters = GeoMath.distanceMeters(reference.lat(), reference.lng(), row.lat(), row.lng());
      distance = (int) Math.round(meters);
      deliversTo = row.delivery() && row.deliveryRadiusKm() != null && meters <= row.deliveryRadiusKm() * 1000;
    }
    CodeTables tables = codeTables();
    String typeIcon =
        tables.types().stream().filter(e -> e.getKey().equals(row.typeCode())).map(Map.Entry::getValue).findFirst().orElse(null);
    return new MarketSummary(
        row.id(),
        row.slug(),
        row.name(),
        codeName(row.typeCode(), typeIcon, tables.typeNames(), locale),
        cuisineNames(row.cuisines(), locale),
        row.logoUrl(),
        row.coverImageUrl(),
        GeoPoint.of(row.lat(), row.lng()),
        row.addressLine(),
        distance,
        deliversTo,
        schedule.isOpen(now),
        schedule.todayHours(now).orElse(null),
        schedule.nextOpening(now).map(DukkanTime::toApi).orElse(null),
        new FulfilmentOptions(
            row.delivery(),
            row.pickup(),
            row.deliveryRadiusKm(),
            row.deliveryFeeCents() == null ? null : Money.eur(row.deliveryFeeCents()),
            row.freeDeliveryFromCents() == null ? null : Money.eur(row.freeDeliveryFromCents()),
            Money.eur(row.minimumOrderCents())),
        promotionCount,
        row.tags());
  }

  private static OpeningSchedule schedule(MarketRow row) {
    return new OpeningSchedule(
        row.hours().stream()
            .map(h -> new OpeningSchedule.Interval(DayOfWeek.of(h.dayOfWeek()), h.opensAt(), h.closesAt()))
            .toList());
  }

  private static MarketInfo info(MarketRow row) {
    return new MarketInfo(
        row.id(),
        row.name(),
        row.status(),
        row.primaryLocale(),
        GeoPoint.of(row.lat(), row.lng()),
        row.delivery(),
        row.pickup(),
        row.deliveryRadiusKm(),
        row.deliveryFeeCents() == null ? 0 : row.deliveryFeeCents(),
        row.freeDeliveryFromCents(),
        row.minimumOrderCents(),
        List.copyOf(row.hours()));
  }
}
