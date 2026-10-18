package de.dukkan.cart.application;

import de.dukkan.promotion.application.PromotionRule;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A priced cart line. */
public record CartLineView(
    UUID productId,
    String name,
    String unitSize,
    @Nullable String imageUrl,
    long unitPriceCents,
    int quantity,
    long subtotalCents,
    long discountCents,
    long totalCents,
    @Nullable PromotionRule promotion,
    boolean available) {}
