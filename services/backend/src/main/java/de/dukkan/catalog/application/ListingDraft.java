package de.dukkan.catalog.application;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Input for a new listing: exactly one of {@code catalogProductId} or {@code newProduct}. */
public record ListingDraft(
    @Nullable UUID catalogProductId, @Nullable NewProduct newProduct, long priceCents, boolean available, int maxOrderQuantity) {}
