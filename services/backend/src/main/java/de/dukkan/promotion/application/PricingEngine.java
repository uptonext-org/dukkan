package de.dukkan.promotion.application;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Pure, side-effect-free cart pricing. All amounts are long cents. A promotion never makes a line
 * more expensive than its undiscounted subtotal.
 */
public final class PricingEngine {

  /** One cart line to price. */
  public record Line(UUID productId, long unitPriceCents, int quantity, @Nullable PromotionRule promotion) {
    public Line {
      if (unitPriceCents < 0) {
        throw new IllegalArgumentException("unitPriceCents must be >= 0");
      }
      if (quantity < 0) {
        throw new IllegalArgumentException("quantity must be >= 0");
      }
    }
  }

  /** A priced line. {@code promotion} is set only if it actually applies to this quantity. */
  public record PricedLine(
      UUID productId,
      long unitPriceCents,
      int quantity,
      long subtotalCents,
      long discountCents,
      long totalCents,
      @Nullable PromotionRule promotion) {}

  /** Totals: {@code total = subtotal - discount + deliveryFee}. */
  public record Result(
      List<PricedLine> lines,
      int itemCount,
      long subtotalCents,
      long discountCents,
      long deliveryFeeCents,
      long totalCents) {

    /** Value counted against the minimum order: after discounts, excluding the delivery fee. */
    public long merchandiseTotalCents() {
      return subtotalCents - discountCents;
    }
  }

  private PricingEngine() {}

  public static Result price(List<Line> lines, long deliveryFeeCents) {
    if (deliveryFeeCents < 0) {
      throw new IllegalArgumentException("deliveryFeeCents must be >= 0");
    }
    List<PricedLine> priced = lines.stream().map(PricingEngine::priceLine).toList();
    long subtotal = priced.stream().mapToLong(PricedLine::subtotalCents).sum();
    long discount = priced.stream().mapToLong(PricedLine::discountCents).sum();
    int itemCount = priced.stream().mapToInt(PricedLine::quantity).sum();
    return new Result(priced, itemCount, subtotal, discount, deliveryFeeCents, subtotal - discount + deliveryFeeCents);
  }

  static PricedLine priceLine(Line line) {
    long subtotal = Math.multiplyExact(line.unitPriceCents(), (long) line.quantity());
    long discount = discountFor(line, subtotal);
    PromotionRule applied = discount > 0 ? line.promotion() : null;
    return new PricedLine(
        line.productId(), line.unitPriceCents(), line.quantity(), subtotal, discount, subtotal - discount, applied);
  }

  private static long discountFor(Line line, long subtotal) {
    PromotionRule rule = line.promotion();
    if (rule == null || line.quantity() == 0) {
      return 0;
    }
    long discount =
        switch (rule) {
          case PromotionRule.PercentOff p ->
              Math.multiplyExact(roundHalfUpDiv(line.unitPriceCents() * p.percentOff(), 100), (long) line.quantity());
          case PromotionRule.MultiBuy m -> subtotal - cheapestCombination(line.unitPriceCents(), line.quantity(), m.tiers());
          case PromotionRule.FixedPrice f ->
              Math.multiplyExact(Math.max(0, line.unitPriceCents() - f.offerPriceCents()), (long) line.quantity());
        };
    return Math.clamp(discount, 0, subtotal);
  }

  /**
   * The cheapest way to pay for {@code quantity} units with tiers and single units (an unbounded
   * knapsack over at most a few hundred units): 7 units with "3 for 5 €" and "7 for 10 €" at 2 €
   * each cost 10 €, 6 units cost 10 € (two times 3), 8 units 12 € (7 + 1).
   */
  static long cheapestCombination(long unitPriceCents, int quantity, List<PromotionRule.Tier> tiers) {
    long[] cost = new long[quantity + 1];
    for (int n = 1; n <= quantity; n++) {
      long best = Math.addExact(cost[n - 1], unitPriceCents);
      for (PromotionRule.Tier tier : tiers) {
        if (tier.quantity() <= n) {
          best = Math.min(best, Math.addExact(cost[n - tier.quantity()], tier.priceCents()));
        }
      }
      cost[n] = best;
    }
    return cost[quantity];
  }

  /** {@code round_half_up(numerator / denominator)} for non-negative values. */
  static long roundHalfUpDiv(long numerator, long denominator) {
    return (numerator + denominator / 2) / denominator;
  }
}
