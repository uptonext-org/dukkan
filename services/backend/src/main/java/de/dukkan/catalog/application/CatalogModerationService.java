package de.dukkan.catalog.application;

import de.dukkan.catalog.domain.SearchText;
import de.dukkan.catalog.infrastructure.CatalogProductRepository;
import de.dukkan.catalog.infrastructure.CategoryRepository;
import de.dukkan.catalog.infrastructure.CategoryRepository.CategoryRow;
import de.dukkan.catalog.infrastructure.ModerationRepository;
import de.dukkan.catalog.infrastructure.ModerationRepository.Duplicate;
import de.dukkan.catalog.infrastructure.ModerationRepository.ProductRow;
import de.dukkan.i18n.application.ContentLocale;
import de.dukkan.i18n.application.LocaleService;
import de.dukkan.i18n.application.TextNormalizer;
import de.dukkan.shared.api.PageResponse;
import de.dukkan.shared.error.ConflictException;
import de.dukkan.shared.error.ErrorCodes;
import de.dukkan.shared.error.InvalidRequestException;
import de.dukkan.shared.error.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin review of products that merchants create (B-36a): correct texts (including machine
 * translations, B-38), keep a product private to its market, or promote it to the shared catalog
 * so every market can list it.
 */
@Service
@Transactional
public class CatalogModerationService {

  public static final List<String> STATUSES = List.of("PENDING", "KEPT_PRIVATE", "PROMOTED");

  /** A product's text in one locale. */
  public record Text(String name, @Nullable String description, boolean machineTranslated) {}

  public record Ref(UUID id, String name) {}

  public record DuplicateRef(UUID id, String name, String unitSize, @Nullable String barcode) {}

  public record Item(
      UUID id,
      @Nullable Ref market,
      @Nullable Ref category,
      @Nullable String brand,
      String unitSize,
      @Nullable String barcode,
      @Nullable String imageUrl,
      String reviewStatus,
      Instant createdAt,
      Map<String, Text> texts,
      List<DuplicateRef> duplicates,
      boolean restrictedGoodsHint) {}

  public enum Decision {
    PROMOTE,
    KEEP_PRIVATE
  }

  private final ModerationRepository moderation;
  private final CatalogProductRepository products;
  private final CategoryRepository categories;
  private final LocaleService locales;
  private final Clock clock;

  public CatalogModerationService(
      ModerationRepository moderation, CatalogProductRepository products, CategoryRepository categories, LocaleService locales, Clock clock) {
    this.moderation = moderation;
    this.products = products;
    this.categories = categories;
    this.locales = locales;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public PageResponse<Item> queue(String status, int page, int size, ContentLocale locale) {
    ModerationRepository.Page found = moderation.findByStatus(status, page, size);
    Map<UUID, List<Duplicate>> duplicates = moderation.duplicates(found.rows());
    Map<UUID, CategoryRow> categoryRows = categoryRows();
    List<Item> items = found.rows().stream().map(r -> item(r, duplicates.getOrDefault(r.id(), List.of()), categoryRows, locale)).toList();
    return PageResponse.of(items, page, size, found.total());
  }

  /**
   * Sets the texts of the given locales (name required, description optional; empty description
   * removes it). Corrected texts are no longer machine translations; the search text is rebuilt.
   */
  public Item updateTexts(UUID productId, Map<String, String> names, Map<String, String> descriptions, ContentLocale locale) {
    ProductRow row = reviewable(productId);
    for (String code : names.keySet()) {
      if (!locales.isEnabled(code)) {
        throw new InvalidRequestException("names", "SupportedLocale", "contains a locale that is not enabled: " + code);
      }
    }
    for (String code : descriptions.keySet()) {
      if (!names.containsKey(code) && !row.texts().containsKey(code)) {
        throw new InvalidRequestException("descriptions", "NameRequired", "a description needs a name in the same locale: " + code);
      }
    }
    Map<String, String> merged = new LinkedHashMap<>();
    row.texts().forEach((code, text) -> merged.put(code, text.name()));
    names.forEach(
        (code, name) -> {
          String value = name.strip();
          if (value.isEmpty() || value.length() > 150) {
            throw new InvalidRequestException("names", "Size", "names must have 1 to 150 characters");
          }
          merged.put(code, value);
        });
    for (String code : merged.keySet()) {
      if (!names.containsKey(code) && !descriptions.containsKey(code)) {
        continue;
      }
      ModerationRepository.TextRow current = row.texts().get(code);
      String description =
          descriptions.containsKey(code)
              ? blankToNull(descriptions.get(code))
              : current == null ? null : current.description();
      moderation.upsertText(productId, code, merged.get(code), description, TextNormalizer.normalize(merged.get(code)));
    }
    products
        .texts(productId)
        .ifPresent(t -> products.updateSearchText(productId, SearchText.build(t.names().values(), t.brand(), t.keywords())));
    return item(productId, locale);
  }

  /** Promotes a pending or private product to the shared catalog, or keeps it private. */
  public Item decide(UUID productId, Decision decision, ContentLocale locale) {
    ProductRow row = reviewable(productId);
    Instant now = clock.instant();
    if (decision == Decision.KEEP_PRIVATE) {
      moderation.setStatus(productId, "KEPT_PRIVATE", false, now);
    } else {
      boolean sameBarcode =
          row.barcode() != null
              && moderation.duplicates(List.of(row)).getOrDefault(productId, List.of()).stream().anyMatch(d -> row.barcode().equals(d.barcode()));
      if (sameBarcode) {
        throw new ConflictException(ErrorCodes.PRODUCT_DUPLICATE, "The shared catalog already has a product with this barcode.");
      }
      // The market keeps its listing; the product now belongs to the shared catalog.
      moderation.setStatus(productId, "PROMOTED", true, now);
    }
    return item(productId, locale);
  }

  private ProductRow reviewable(UUID productId) {
    ProductRow row = moderation.find(productId).orElseThrow(() -> new NotFoundException("Catalog product not found."));
    if (row.ownerMarketId() == null) {
      // Shared products (curated or already promoted) are not part of the review.
      throw new ConflictException(ErrorCodes.INVALID_STATUS_TRANSITION, "This product is already in the shared catalog.");
    }
    return row;
  }

  private Item item(UUID productId, ContentLocale locale) {
    ProductRow row = moderation.find(productId).orElseThrow(() -> new NotFoundException("Catalog product not found."));
    return item(row, moderation.duplicates(List.of(row)).getOrDefault(productId, List.of()), categoryRows(), locale);
  }

  private Item item(ProductRow row, List<Duplicate> duplicates, Map<UUID, CategoryRow> categoryRows, ContentLocale locale) {
    Map<String, Text> texts = new LinkedHashMap<>();
    row.texts().forEach((code, t) -> texts.put(code, new Text(t.name(), t.description(), t.machineTranslated())));
    CategoryRow category = categoryRows.get(row.categoryId());
    String categoryName = category == null ? null : locale.pick(category.names(), "de");
    return new Item(
        row.id(),
        row.ownerMarketId() == null || row.marketName() == null ? null : new Ref(row.ownerMarketId(), row.marketName()),
        categoryName == null ? null : new Ref(row.categoryId(), categoryName),
        row.brand(),
        row.unitSize(),
        row.barcode(),
        row.imageUrl(),
        row.reviewStatus(),
        row.createdAt(),
        texts,
        duplicates.stream().map(d -> new DuplicateRef(d.id(), d.name(), d.unitSize(), d.barcode())).toList(),
        RestrictedGoods.suspected(texts.values().stream().map(Text::name).toArray(String[]::new))
            || RestrictedGoods.suspected(row.brand()));
  }

  private Map<UUID, CategoryRow> categoryRows() {
    Map<UUID, CategoryRow> rows = new LinkedHashMap<>();
    categories.findAll().forEach(c -> rows.put(c.id(), c));
    return rows;
  }

  private static @Nullable String blankToNull(@Nullable String text) {
    return text == null || text.isBlank() ? null : text.strip();
  }
}
