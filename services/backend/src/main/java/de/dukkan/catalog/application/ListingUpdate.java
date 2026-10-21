package de.dukkan.catalog.application;

import org.jspecify.annotations.Nullable;

/** Partial listing update; null fields stay unchanged. */
public record ListingUpdate(@Nullable Long priceCents, @Nullable Boolean available, @Nullable Integer maxOrderQuantity) {}
