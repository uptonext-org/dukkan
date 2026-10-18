package de.dukkan.promotion.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Read model of an active promotion. */
public record PromotionView(
    UUID id,
    String title,
    @Nullable String description,
    PromotionRule rule,
    Instant validFrom,
    Instant validUntil,
    List<UUID> productIds) {}
