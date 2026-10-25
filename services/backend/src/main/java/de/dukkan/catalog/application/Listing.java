package de.dukkan.catalog.application;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import org.jspecify.annotations.Nullable;

/**
 * Contract schema {@code Listing}: ProductSummary plus whether the market created the product and
 * the market's own details of the listing ({@code null}: none, the catalog values show).
 */
public record Listing(@JsonUnwrapped ProductSummary summary, boolean privateProduct, @Nullable ListingOverrides overrides) {}
