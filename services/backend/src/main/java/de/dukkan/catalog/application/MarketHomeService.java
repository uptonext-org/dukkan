package de.dukkan.catalog.application;

import de.dukkan.i18n.application.ContentLocale;
import de.dukkan.i18n.application.UiTexts;
import de.dukkan.market.application.MarketInfo;
import de.dukkan.market.application.MarketService;
import de.dukkan.promotion.application.PromotionService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Composes a market's start page: banners, then PROMOTIONS, NEW, FREQUENTLY_ORDERED (signed-in
 * users) and the market's largest categories. Empty sections are left out. Lives in catalog because
 * every section is a list of products.
 */
@Service
@Transactional(readOnly = true)
public class MarketHomeService {

  static final int SECTION_SIZE = 10;
  static final int CATEGORY_SECTIONS = 3;

  private final MarketService markets;
  private final CatalogService catalog;
  private final PromotionService promotions;
  private final PurchaseHistory purchases;
  private final UiTexts texts;

  public MarketHomeService(
      MarketService markets, CatalogService catalog, PromotionService promotions, PurchaseHistory purchases, UiTexts texts) {
    this.markets = markets;
    this.catalog = catalog;
    this.promotions = promotions;
    this.purchases = purchases;
    this.texts = texts;
  }

  public MarketHome home(UUID marketId, ContentLocale locale, @Nullable UUID userId) {
    MarketInfo market = markets.activeInfo(marketId);
    List<HomeSection> sections = new ArrayList<>();

    List<UUID> promoted = new ArrayList<>(promotions.activeListingIds(marketId));
    List<ProductSummary> promotedItems =
        catalog.summaries(promoted, locale, userId).stream()
            .filter(ProductSummary::available)
            .sorted(Comparator.comparing(ProductSummary::name))
            .limit(SECTION_SIZE)
            .toList();
    add(sections, "PROMOTIONS", locale, promotedItems);
    add(sections, "NEW", locale, catalog.summaries(catalog.newListingIds(marketId, SECTION_SIZE), locale, userId));
    if (userId != null) {
      List<UUID> frequent = purchases.frequentlyOrderedListingIds(userId, marketId);
      add(
          sections,
          "FREQUENTLY_ORDERED",
          locale,
          catalog.summaries(frequent.subList(0, Math.min(SECTION_SIZE, frequent.size())), locale, userId));
    }

    Map<UUID, Integer> counts = catalog.categoryCounts(marketId);
    List<UUID> topCategories =
        counts.entrySet().stream()
            .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed().thenComparing(e -> e.getKey().toString()))
            .limit(CATEGORY_SECTIONS)
            .map(Map.Entry::getKey)
            .toList();
    List<CategoryView> categories = catalog.categories(topCategories, locale, market.primaryLocale());
    for (UUID categoryId : topCategories) {
      CategoryView category = categories.stream().filter(c -> c.id().equals(categoryId)).findFirst().orElse(null);
      if (category == null) {
        continue;
      }
      List<ProductSummary> items =
          catalog.summaries(
              catalog.categoryListingIds(marketId, categoryId, locale.chain(market.primaryLocale()), SECTION_SIZE),
              locale,
              userId);
      if (!items.isEmpty()) {
        sections.add(new HomeSection("CATEGORY", category.name(), null, categoryId, items));
      }
    }
    return new MarketHome(markets.summary(marketId, locale), markets.banners(marketId, locale), sections);
  }

  private void add(List<HomeSection> sections, String key, ContentLocale locale, List<ProductSummary> items) {
    if (items.isEmpty()) {
      return;
    }
    String subtitleKey = "home.section." + key + ".subtitle";
    String subtitle = texts.text(subtitleKey, locale);
    sections.add(
        new HomeSection(
            key,
            texts.text("home.section." + key + ".title", locale),
            subtitle.equals(subtitleKey) ? null : subtitle,
            null,
            items));
  }
}
