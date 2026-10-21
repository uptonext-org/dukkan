package de.dukkan.cart.application;

import de.dukkan.catalog.application.PromotionBadge;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A priced cart line. */
public record CartLineView(
    UUID listingId,
    UUID catalogProductId,
    String name,
    String unitSize,
    @Nullable String imageUrl,
    long unitPriceCents,
    int quantity,
    long subtotalCents,
    long discountCents,
    long totalCents,
    @Nullable PromotionBadge promotion,
    boolean available,
    int maxOrderQuantity) {}
