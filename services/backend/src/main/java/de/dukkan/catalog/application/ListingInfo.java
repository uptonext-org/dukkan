package de.dukkan.catalog.application;

import de.dukkan.promotion.application.PromotionRule;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Pricing-relevant facts of a listing for carts and orders. {@code available} is false for
 * unavailable and deleted listings.
 */
public record ListingInfo(
    UUID id,
    UUID marketId,
    UUID catalogProductId,
    UUID categoryId,
    String name,
    String unitSize,
    @Nullable String imageUrl,
    @Nullable String iconKey,
    long priceCents,
    @Nullable PromotionRule promotion,
    @Nullable PromotionBadge promotionBadge,
    boolean available,
    boolean deleted,
    int maxOrderQuantity) {}
