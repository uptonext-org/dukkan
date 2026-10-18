package de.dukkan.catalog.api;

import de.dukkan.catalog.application.CategoryView;
import de.dukkan.catalog.application.ProductPageView;
import de.dukkan.catalog.application.ProductView;
import de.dukkan.promotion.api.PromotionBadge;
import de.dukkan.shared.api.Money;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Response bodies of the catalog endpoints (contract schemas of the same names). */
final class CatalogDtos {

  private CatalogDtos() {}

  record CategoryResponse(
      UUID id, @Nullable UUID parentId, String slug, String name, @Nullable String iconKey, int sortOrder) {
    static CategoryResponse from(CategoryView c) {
      return new CategoryResponse(c.id(), c.parentId(), c.slug(), c.name(), c.iconKey(), c.sortOrder());
    }
  }

  record UnitPrice(Money price, String unit) {}

  record ProductSummary(
      UUID id,
      String sku,
      String name,
      @Nullable String brand,
      String unitSize,
      @Nullable String imageUrl,
      Money price,
      @Nullable UnitPrice comparisonPrice,
      @Nullable PromotionBadge promotion,
      boolean available) {
    static ProductSummary from(ProductView p) {
      return new ProductSummary(
          p.id(),
          p.sku(),
          p.name(),
          p.brand(),
          p.unitSize(),
          p.imageUrl(),
          Money.eur(p.priceCents()),
          unitPrice(p),
          PromotionBadge.from(p.promotion()),
          p.available());
    }
  }

  record ProductDetail(
      UUID id,
      String sku,
      String name,
      @Nullable String brand,
      String unitSize,
      @Nullable String imageUrl,
      Money price,
      @Nullable UnitPrice comparisonPrice,
      @Nullable PromotionBadge promotion,
      boolean available,
      UUID categoryId,
      @Nullable String description,
      @Nullable String ingredients,
      List<String> allergens,
      @Nullable String origin,
      int maxOrderQuantity) {
    static ProductDetail from(ProductView p) {
      return new ProductDetail(
          p.id(),
          p.sku(),
          p.name(),
          p.brand(),
          p.unitSize(),
          p.imageUrl(),
          Money.eur(p.priceCents()),
          unitPrice(p),
          PromotionBadge.from(p.promotion()),
          p.available(),
          p.categoryId(),
          p.description(),
          p.ingredients(),
          p.allergens(),
          p.origin(),
          p.maxOrderQuantity());
    }
  }

  record ProductPage(List<ProductSummary> items, int page, int size, long totalItems, int totalPages) {
    static ProductPage from(ProductPageView page) {
      return new ProductPage(
          page.items().stream().map(ProductSummary::from).toList(),
          page.page(),
          page.size(),
          page.totalItems(),
          page.totalPages());
    }
  }

  private static @Nullable UnitPrice unitPrice(ProductView p) {
    return p.comparisonPrice() == null
        ? null
        : new UnitPrice(Money.eur(p.comparisonPrice().amountCents()), p.comparisonPrice().unit());
  }
}
