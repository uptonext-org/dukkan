package de.dukkan.promotion.application;

import de.dukkan.shared.api.Money;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A promotion with localised title (contract schema {@code Promotion}). */
public record PromotionView(
    UUID id,
    UUID marketId,
    String title,
    @Nullable String description,
    String type,
    @Nullable Integer percentOff,
    @Nullable Integer bundleQuantity,
    @Nullable Money bundlePrice,
    OffsetDateTime validFrom,
    OffsetDateTime validUntil,
    List<UUID> productIds) {}
