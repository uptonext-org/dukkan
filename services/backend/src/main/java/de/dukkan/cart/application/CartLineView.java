package de.dukkan.cart.application;

import de.dukkan.catalog.application.PromotionBadge;
import de.dukkan.promotion.application.PromotionRule;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A priced cart line; {@code appliedRule} is the promotion that lowered its price, if any. */
public record CartLineView(
    UUID listingId,
    UUID catalogProductId,
    String name,
    String unitSize,
    @Nullable String imageUrl,
    @Nullable String iconKey,
    long unitPriceCents,
    int quantity,
    long subtotalCents,
    long discountCents,
    long totalCents,
    @Nullable PromotionBadge promotion,
    boolean available,
    int maxOrderQuantity,
    @Nullable PromotionRule appliedRule) {}
