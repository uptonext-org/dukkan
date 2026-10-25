package de.dukkan.order.application;

import de.dukkan.order.domain.OrderItem;
import de.dukkan.promotion.application.PricingEngine;
import de.dukkan.promotion.application.PromotionRule;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * What a line costs when the market hands over fewer units than ordered: the cheaper of the ordered
 * total scaled down (rounded down) and the units re-priced with the promotion that applied when
 * ordering. So a missing item never makes the rest dearer: "3 for 5 €" with 4 ordered and 3 handed
 * over costs 5 €, not three quarters of 7 €.
 */
public final class ChargedPrice {

  /** Placeholder id: the rule is rebuilt from the order snapshot, not looked up. */
  private static final UUID SNAPSHOT = new UUID(0, 0);

  private ChargedPrice() {}

  public static long of(long unitPriceCents, int ordered, long lineTotalCents, OrderItem.AppliedPromotion promotion, int units) {
    if (units >= ordered) {
      return lineTotalCents;
    }
    long proRata = Math.floorDiv(lineTotalCents * units, ordered);
    PricingEngine.Result repriced = PricingEngine.price(List.of(new PricingEngine.Line(SNAPSHOT, unitPriceCents, units, rule(promotion))), 0);
    return Math.min(proRata, repriced.merchandiseTotalCents());
  }

  static long of(OrderItem item, int units) {
    return of(item.getUnitPriceCents(), item.getQuantity(), item.getLineTotalCents(), item.getPromotion(), units);
  }

  /** The snapshot of the rule that applied when ordering. */
  public static OrderItem.AppliedPromotion snapshot(@Nullable PromotionRule rule) {
    return switch (rule) {
      case null -> OrderItem.AppliedPromotion.NONE;
      case PromotionRule.PercentOff p -> new OrderItem.AppliedPromotion(p.percentOff(), null, null, null, null);
      case PromotionRule.MultiBuy m ->
          new OrderItem.AppliedPromotion(
              null,
              m.bundleQuantity(),
              m.bundlePriceCents(),
              null,
              m.tiers().stream().map(t -> t.quantity() + ":" + t.priceCents()).collect(Collectors.joining(";")));
      case PromotionRule.FixedPrice f -> new OrderItem.AppliedPromotion(null, null, null, f.offerPriceCents(), null);
    };
  }

  private static @Nullable PromotionRule rule(OrderItem.AppliedPromotion promotion) {
    if (promotion.percentOff() != null) {
      return new PromotionRule.PercentOff(SNAPSHOT, promotion.percentOff());
    }
    if (promotion.tiers() != null) {
      List<PromotionRule.Tier> tiers =
          Arrays.stream(promotion.tiers().split(";"))
              .map(t -> t.split(":"))
              .map(t -> new PromotionRule.Tier(Integer.parseInt(t[0]), Long.parseLong(t[1])))
              .toList();
      return new PromotionRule.MultiBuy(SNAPSHOT, tiers);
    }
    if (promotion.bundleQuantity() != null && promotion.bundlePriceCents() != null) {
      return new PromotionRule.MultiBuy(SNAPSHOT, promotion.bundleQuantity(), promotion.bundlePriceCents());
    }
    if (promotion.offerPriceCents() != null) {
      return new PromotionRule.FixedPrice(SNAPSHOT, promotion.offerPriceCents());
    }
    return null;
  }
}
