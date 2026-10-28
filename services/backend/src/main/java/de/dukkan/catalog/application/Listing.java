package de.dukkan.catalog.application;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import org.jspecify.annotations.Nullable;

/**
 * Contract schema {@code Listing}: ProductSummary plus whether the market created the product, the
 * market's own details of the listing ({@code null}: none, the catalog values show) and whether
 * its name looks like alcohol or tobacco ({@link RestrictedGoods}).
 */
public record Listing(
    @JsonUnwrapped ProductSummary summary, boolean privateProduct, @Nullable ListingOverrides overrides, boolean restrictedGoodsHint) {}
