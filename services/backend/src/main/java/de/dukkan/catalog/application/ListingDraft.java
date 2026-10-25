package de.dukkan.catalog.application;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Input for a new listing: exactly one of {@code catalogProductId} or {@code newProduct}. Without
 * {@code maxOrderQuantity} the market's default applies. {@code overrides} adjust a catalog product
 * for this market only.
 */
public record ListingDraft(
    @Nullable UUID catalogProductId,
    @Nullable NewProduct newProduct,
    long priceCents,
    boolean available,
    @Nullable Integer maxOrderQuantity,
    ListingOverrides overrides) {

  public ListingDraft(
      @Nullable UUID catalogProductId, @Nullable NewProduct newProduct, long priceCents, boolean available, @Nullable Integer maxOrderQuantity) {
    this(catalogProductId, newProduct, priceCents, available, maxOrderQuantity, ListingOverrides.NONE);
  }
}
