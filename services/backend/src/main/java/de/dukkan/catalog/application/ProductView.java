package de.dukkan.catalog.application;

import de.dukkan.catalog.domain.ComparisonPrice;
import de.dukkan.promotion.application.PromotionRule;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Read model of a product with its current price and winning promotion (if any). */
public record ProductView(
    UUID id,
    String sku,
    String name,
    @Nullable String brand,
    String unitSize,
    @Nullable String imageUrl,
    long priceCents,
    @Nullable ComparisonPrice comparisonPrice,
    @Nullable PromotionRule promotion,
    boolean available,
    UUID categoryId,
    @Nullable String description,
    @Nullable String ingredients,
    List<String> allergens,
    @Nullable String origin,
    int maxOrderQuantity) {}
