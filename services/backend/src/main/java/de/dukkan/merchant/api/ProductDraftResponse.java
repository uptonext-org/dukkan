package de.dukkan.merchant.api;

import de.dukkan.capture.application.DraftEngine;
import de.dukkan.capture.application.DraftProduct;
import de.dukkan.capture.application.DraftWarning;
import de.dukkan.capture.application.ProductDraftResult;
import de.dukkan.catalog.application.CatalogProduct;
import de.dukkan.shared.api.Money;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** Contract schema {@code ProductDraft}. */
record ProductDraftResponse(
    DraftEngine engine, Product product, @Nullable Money price, List<CatalogProduct> catalogMatches, List<DraftWarning> warnings) {

  /** Contract schema {@code ProductDraftProduct}. */
  record Product(
      Map<String, String> name,
      Map<String, String> description,
      List<String> keywords,
      @Nullable String brand,
      @Nullable String unitSize,
      @Nullable BigDecimal netQuantity,
      @Nullable String netUnit,
      @Nullable UUID categoryId,
      List<String> cuisineCodes,
      boolean organic,
      List<String> allergens,
      @Nullable String imageUrl,
      @Nullable String barcode) {}

  static ProductDraftResponse of(ProductDraftResult result, Function<String, String> urlForKey) {
    DraftProduct p = result.product();
    return new ProductDraftResponse(
        result.engine(),
        new Product(
            p.name(),
            p.description(),
            p.keywords(),
            p.brand(),
            p.unitSize(),
            p.netQuantity(),
            p.netUnit(),
            p.categoryId(),
            p.cuisineCodes(),
            p.organic(),
            p.allergens(),
            result.imageKey() == null ? null : urlForKey.apply(result.imageKey()),
            p.barcode()),
        result.priceCents() == null ? null : Money.eur(result.priceCents()),
        result.catalogMatches(),
        result.warnings());
  }
}
