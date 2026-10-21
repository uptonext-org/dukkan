package de.dukkan.catalog.application;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

/** Contract schema {@code Listing}: ProductSummary plus whether the market created the product. */
public record Listing(@JsonUnwrapped ProductSummary summary, boolean privateProduct) {}
