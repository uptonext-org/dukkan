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
    List<TierView> tiers,
    List<OfferView> offers,
    OffsetDateTime validFrom,
    OffsetDateTime validUntil,
    @Nullable List<Integer> weekdays,
    @Nullable String dailyFrom,
    @Nullable String dailyUntil,
    List<UUID> productIds) {

  /** Contract schema {@code PromotionTier}: {@code quantity} units together for {@code price}. */
  public record TierView(int quantity, Money price) {}

  /** Contract schema {@code PromotionOffer}: the offer price of one product. */
  public record OfferView(UUID productId, Money offerPrice) {}
}
