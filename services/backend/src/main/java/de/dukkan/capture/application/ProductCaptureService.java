package de.dukkan.capture.application;

import de.dukkan.capture.application.ProductDraftExtractor.CategoryOption;
import de.dukkan.capture.application.ProductDraftExtractor.Image;
import de.dukkan.capture.application.ProductDraftExtractor.Outcome;
import de.dukkan.capture.domain.Allergens;
import de.dukkan.capture.domain.ExtractedProduct;
import de.dukkan.capture.domain.RuleBasedDraftParser;
import de.dukkan.catalog.application.CatalogProduct;
import de.dukkan.catalog.application.CatalogService;
import de.dukkan.catalog.application.CategoryView;
import de.dukkan.i18n.application.ContentLocale;
import de.dukkan.i18n.application.LocaleService;
import de.dukkan.i18n.application.SupportedLocale;
import de.dukkan.i18n.application.TextNormalizer;
import de.dukkan.market.application.MarketService;
import de.dukkan.media.application.MediaObject;
import de.dukkan.media.application.MediaService;
import de.dukkan.shared.api.CodeName;
import de.dukkan.shared.config.DukkanProperties;
import de.dukkan.shared.error.InvalidRequestException;
import de.dukkan.shared.ratelimit.FixedWindowRateLimiter;
import de.dukkan.shared.ratelimit.RateLimitStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * AI product capture (B-33): turns a merchant's text and/or photo into a {@link ProductDraftResult}
 * that is never saved. The language model is optional; the rule-based parser covers its absence
 * and its failures. Every value is checked against the known codes and lengths of the contract,
 * so a draft can always be turned into a valid {@code NewCatalogProduct} once the merchant has
 * filled the missing fields. Callers check market ownership first.
 *
 * <p>No transaction is held here: the model call can take many seconds, and the catalog reads use
 * their own read-only transactions.
 */
@Service
public class ProductCaptureService {

  private static final Logger LOG = LoggerFactory.getLogger(ProductCaptureService.class);

  private static final int MAX_MATCHES = 5;
  private static final int MAX_MATCH_QUERIES = 6;
  private static final Pattern BARCODE = Pattern.compile("^\\d{8,14}$");
  private static final Pattern NON_LETTERS = Pattern.compile("[^\\p{L}\\p{N}]+");
  private static final Set<String> NET_UNITS = Set.of("kg", "l", "piece");
  private static final BigDecimal MAX_NET_QUANTITY = new BigDecimal("100000");
  private static final long MAX_PRICE_CENTS = 10_000_000L;
  /** Words too generic to look up on their own in the shared catalog. */
  private static final Set<String> GENERIC_WORDS =
      Set.of("bio", "organic", "frisch", "fresh", "neu", "new", "gross", "klein", "packung", "pack", "stuck", "stueck", "piece",
          "عضوي", "طازج", "جديد", "taze", "yeni");

  private final ProductDraftExtractor ai;
  private final CatalogService catalog;
  private final MarketService markets;
  private final LocaleService locales;
  private final MediaService media;
  private final FixedWindowRateLimiter<UUID> rateLimiter;

  public ProductCaptureService(
      ProductDraftExtractor ai,
      CatalogService catalog,
      MarketService markets,
      LocaleService locales,
      MediaService media,
      DukkanProperties properties,
      Clock clock,
      RateLimitStore rateLimits) {
    this.ai = ai;
    this.catalog = catalog;
    this.markets = markets;
    this.locales = locales;
    this.media = media;
    this.rateLimiter = new FixedWindowRateLimiter<>("product-drafts", properties.capture().draftsPerHour(), Duration.ofHours(1), clock, rateLimits);
  }

  /**
   * Builds a draft. {@code marketId} is the caller's market (ownership already checked), used for
   * {@code alreadyListed}; {@code primaryLocale} is the market's primary locale, the name's locale
   * for Latin-script text.
   */
  public ProductDraftResult draft(UUID userId, UUID marketId, String primaryLocale, ProductDraftInput input, ContentLocale locale) {
    String text = input.text() == null || input.text().isBlank() ? null : input.text().strip();
    if (text == null && input.imageKey() == null) {
      throw new InvalidRequestException("text", "Required", "Send a text, an imageKey or both.");
    }
    rateLimiter.acquire(userId, "product drafts");
    Image image = input.imageKey() == null ? null : loadImage(input.imageKey());

    List<String> localeCodes = localeCodes(primaryLocale);
    List<CategoryView> categoryViews = catalog.allCategories(locale);
    Map<String, UUID> categoryIds = new HashMap<>();
    categoryViews.forEach(c -> categoryIds.put(c.slug(), c.id()));
    List<String> cuisineCodes = markets.cuisines(locale).stream().map(CodeName::code).toList();

    ExtractedProduct rules = RuleBasedDraftParser.parse(text, latinLocale(primaryLocale, locale));
    Set<DraftWarning> warnings = EnumSet.noneOf(DraftWarning.class);
    DraftEngine engine = DraftEngine.RULES;
    ExtractedProduct extracted = rules;
    boolean imageAnalysed = false;

    if (!ai.enabled()) {
      warnings.add(DraftWarning.AI_NOT_CONFIGURED);
    } else {
      Outcome outcome =
          ai.extract(
              new ProductDraftExtractor.Request(
                  text, image, localeCodes, categoryOptions(localeCodes), cuisineCodes, Allergens.CODES));
      switch (outcome) {
        case Outcome.Extracted(ExtractedProduct product, boolean analysed) -> {
          engine = DraftEngine.AI;
          extracted = withRuleFallbacks(product, rules);
          imageAnalysed = analysed;
        }
        case Outcome.Declined() -> warnings.add(DraftWarning.AI_DECLINED);
        case Outcome.Failed() -> warnings.add(DraftWarning.AI_UNAVAILABLE);
      }
    }
    if (image != null && !imageAnalysed) {
      warnings.add(DraftWarning.IMAGE_NOT_ANALYSED);
    }

    DraftProduct product = sanitize(extracted, localeCodes, categoryIds, Set.copyOf(cuisineCodes));
    Long price = extracted.priceCents() != null && extracted.priceCents() > 0 && extracted.priceCents() <= MAX_PRICE_CENTS ? extracted.priceCents() : null;
    if (product.name().isEmpty()) {
      warnings.add(DraftWarning.NAME_MISSING);
    }
    if (product.categoryId() == null) {
      warnings.add(DraftWarning.CATEGORY_UNCERTAIN);
    }
    if (product.unitSize() == null) {
      warnings.add(DraftWarning.UNIT_SIZE_MISSING);
    }
    if (price == null) {
      warnings.add(DraftWarning.PRICE_MISSING);
    }
    List<CatalogProduct> matches = catalogMatches(product, marketId, locale, primaryLocale);
    LOG.info("Product draft for market {}: engine {}, {} matches, warnings {}", marketId, engine, matches.size(), warnings);
    return new ProductDraftResult(engine, product, price, matches, List.copyOf(warnings), input.imageKey());
  }

  private Image loadImage(String key) {
    MediaObject object =
        media.open(key).orElseThrow(() -> new InvalidRequestException("imageKey", "NotFound", "No uploaded image with this key."));
    try {
      return new Image(object.content().getContentAsByteArray(), object.contentType());
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read image " + key, e);
    }
  }

  /** Enabled locales, the market's primary locale first. */
  private List<String> localeCodes(String primaryLocale) {
    List<String> codes = new ArrayList<>();
    for (SupportedLocale supported : locales.enabled()) {
      codes.add(supported.code());
    }
    if (codes.remove(primaryLocale)) {
      codes.addFirst(primaryLocale);
    }
    return List.copyOf(codes);
  }

  private List<CategoryOption> categoryOptions(List<String> localeCodes) {
    Map<String, Map<String, String>> names = new LinkedHashMap<>();
    for (String code : localeCodes) {
      for (CategoryView view : catalog.allCategories(ContentLocale.of(code))) {
        names.computeIfAbsent(view.slug(), k -> new LinkedHashMap<>()).put(code, view.name());
      }
    }
    return names.entrySet().stream().map(e -> new CategoryOption(e.getKey(), Map.copyOf(e.getValue()))).toList();
  }

  /** A Latin-script name belongs to the market's language, or the request's, unless both are Arabic. */
  private static String latinLocale(String primaryLocale, ContentLocale locale) {
    if (!"ar".equals(primaryLocale)) {
      return primaryLocale;
    }
    return "ar".equals(locale.effective()) ? ContentLocale.DEFAULT : locale.effective();
  }

  /** Price and pack size typed by the merchant are parsed reliably by the rules; the model may omit them. */
  private static ExtractedProduct withRuleFallbacks(ExtractedProduct model, ExtractedProduct rules) {
    boolean sizeFromRules = model.unitSize() == null && rules.unitSize() != null;
    return new ExtractedProduct(
        model.names(),
        model.descriptions(),
        model.brand(),
        sizeFromRules ? rules.unitSize() : model.unitSize(),
        sizeFromRules ? rules.netQuantity() : model.netQuantity(),
        sizeFromRules ? rules.netUnit() : model.netUnit(),
        model.categorySlug() != null ? model.categorySlug() : rules.categorySlug(),
        model.cuisineCodes(),
        model.organic(),
        model.allergens(),
        model.keywords(),
        model.priceCents() != null ? model.priceCents() : rules.priceCents(),
        model.barcode());
  }

  private static DraftProduct sanitize(
      ExtractedProduct in, List<String> localeCodes, Map<String, UUID> categoryIds, Set<String> knownCuisines) {
    BigDecimal quantity = in.netQuantity();
    String netUnit = in.netUnit() != null && NET_UNITS.contains(in.netUnit()) ? in.netUnit() : null;
    if (quantity == null || netUnit == null || quantity.signum() <= 0 || quantity.compareTo(MAX_NET_QUANTITY) > 0) {
      quantity = null;
      netUnit = null;
    }
    List<String> keywords =
        in.keywords().stream()
            .map(String::strip)
            .filter(k -> !k.isEmpty() && k.length() <= 60)
            .distinct()
            .limit(30)
            .toList();
    return new DraftProduct(
        localized(in.names(), localeCodes, 120),
        localized(in.descriptions(), localeCodes, 2000),
        keywords,
        text(in.brand(), 80),
        text(in.unitSize(), 40),
        quantity == null ? null : quantity.stripTrailingZeros(),
        netUnit,
        in.categorySlug() == null ? null : categoryIds.get(in.categorySlug()),
        in.cuisineCodes().stream().filter(knownCuisines::contains).distinct().limit(5).toList(),
        in.organic(),
        in.allergens().stream().filter(Allergens.CODES::contains).distinct().toList(),
        in.barcode() != null && BARCODE.matcher(in.barcode().strip()).matches() ? in.barcode().strip() : null);
  }

  /** Only enabled locales, in {@code localeCodes} order, trimmed; blank values are dropped. */
  private static Map<String, String> localized(Map<String, String> values, List<String> localeCodes, int maxLength) {
    Map<String, String> out = new LinkedHashMap<>();
    for (String code : localeCodes) {
      String value = text(values.get(code), maxLength);
      if (value != null) {
        out.put(code, value);
      }
    }
    return out;
  }

  private static @Nullable String text(@Nullable String value, int maxLength) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String stripped = value.strip();
    return stripped.length() <= maxLength ? stripped : stripped.substring(0, maxLength).strip();
  }

  /**
   * Shared catalog products that look like the draft. The catalog search matches a whole term, so
   * the full names are tried first and then their single words; products found by more queries rank
   * higher, the same category breaks ties.
   */
  private List<CatalogProduct> catalogMatches(DraftProduct product, UUID marketId, ContentLocale locale, String primaryLocale) {
    Set<String> queries = new LinkedHashSet<>();
    List<String> names = new ArrayList<>();
    for (String code : List.of(locale.effective(), primaryLocale)) {
      if (product.name().containsKey(code)) {
        names.add(product.name().get(code));
      }
    }
    names.addAll(product.name().values());
    names.forEach(name -> queries.add(name.strip()));
    for (String name : names) {
      for (String word : NON_LETTERS.splitAsStream(name).toList()) {
        String normalized = TextNormalizer.normalize(word);
        if (normalized.length() >= 3 && !GENERIC_WORDS.contains(normalized) && word.codePoints().anyMatch(Character::isLetter)) {
          queries.add(word);
        }
      }
    }
    Map<UUID, CatalogProduct> found = new LinkedHashMap<>();
    Map<UUID, Integer> hits = new HashMap<>();
    queries.stream()
        .limit(MAX_MATCH_QUERIES)
        .forEach(
            query ->
                catalog.sharedCatalog(query, null, null, marketId, 0, 10, locale).items().forEach(item -> {
                  found.putIfAbsent(item.id(), item);
                  hits.merge(item.id(), 1, Integer::sum);
                }));
    Comparator<CatalogProduct> byHits = Comparator.comparingInt(p -> -hits.get(p.id()));
    Comparator<CatalogProduct> sameCategoryFirst = Comparator.comparing(p -> !p.categoryId().equals(product.categoryId()));
    return found.values().stream().sorted(byHits.thenComparing(sameCategoryFirst)).limit(MAX_MATCHES).toList();
  }
}
