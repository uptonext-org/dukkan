package de.dukkan.promotion.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Input for creating a promotion; listing ids must already be validated against the market. */
public record PromotionDraft(
    Map<String, String> title,
    Map<String, String> description,
    String type,
    @Nullable Integer percentOff,
    @Nullable Integer bundleQuantity,
    @Nullable Long bundlePriceCents,
    Instant validFrom,
    Instant validUntil,
    List<UUID> listingIds) {}
