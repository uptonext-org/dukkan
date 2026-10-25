package de.dukkan.promotion.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.dukkan.promotion.application.PricingEngine.Line;
import de.dukkan.promotion.application.PricingEngine.PricedLine;
import de.dukkan.promotion.application.PricingEngine.Result;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PricingEngineTest {

  private static final UUID PRODUCT = UUID.fromString("00000000-0000-4000-8000-000000000001");
  private static final UUID PROMO = UUID.fromString("00000000-0000-4000-8000-0000000000aa");

  private static PricedLine priceOne(long unitPrice, int quantity, PromotionRule rule) {
    return PricingEngine.price(List.of(new Line(PRODUCT, unitPrice, quantity, rule)), 0).lines().getFirst();
  }

  @Nested
  class WithoutPromotion {

    @Test
    void lineTotalIsUnitPriceTimesQuantity() {
      PricedLine line = priceOne(249, 3, null);

      assertThat(line.subtotalCents()).isEqualTo(747);
      assertThat(line.discountCents()).isZero();
      assertThat(line.totalCents()).isEqualTo(747);
      assertThat(line.promotion()).isNull();
    }

    @Test
    void zeroQuantityCostsNothing() {
      PricedLine line = priceOne(249, 0, new PromotionRule.PercentOff(PROMO, 20));

      assertThat(line.subtotalCents()).isZero();
      assertThat(line.discountCents()).isZero();
      assertThat(line.totalCents()).isZero();
      assertThat(line.promotion()).isNull();
    }

    @Test
    void emptyCartHasOnlyTheDeliveryFee() {
      Result result = PricingEngine.price(List.of(), 199);

      assertThat(result.itemCount()).isZero();
      assertThat(result.subtotalCents()).isZero();
      assertThat(result.totalCents()).isEqualTo(199);
      assertThat(result.merchandiseTotalCents()).isZero();
    }
  }

  @Nested
  class PercentOff {

    @ParameterizedTest(name = "{0} cents at {1}% -> {2} cents off per unit")
    @CsvSource({
      // unitPrice, percent, expected per-unit discount (round half up)
      "249, 20, 50", // 49.8  -> 50
      "125, 10, 13", // 12.5  -> 13 (half rounds up)
      "124, 10, 12", // 12.4  -> 12
      "199, 15, 30", // 29.85 -> 30
      "1, 50, 1", //    0.5   -> 1
      "1, 49, 0", //    0.49  -> 0
      "1000, 90, 900",
      "0, 30, 0"
    })
    void roundsEachUnitDiscountHalfUp(long unitPrice, int percent, long perUnitDiscount) {
      PricedLine line = priceOne(unitPrice, 1, new PromotionRule.PercentOff(PROMO, percent));

      assertThat(line.discountCents()).isEqualTo(perUnitDiscount);
      assertThat(line.totalCents()).isEqualTo(unitPrice - perUnitDiscount);
    }

    @Test
    void roundsPerUnitBeforeMultiplyingByQuantity() {
      // 125 * 10% = 12.5 -> 13 per unit; 3 units -> 39 (not round(37.5) = 38).
      PricedLine line = priceOne(125, 3, new PromotionRule.PercentOff(PROMO, 10));

      assertThat(line.subtotalCents()).isEqualTo(375);
      assertThat(line.discountCents()).isEqualTo(39);
      assertThat(line.totalCents()).isEqualTo(336);
      assertThat(line.promotion()).isEqualTo(new PromotionRule.PercentOff(PROMO, 10));
    }

    @Test
    void zeroPriceProductGetsNoBadge() {
      PricedLine line = priceOne(0, 2, new PromotionRule.PercentOff(PROMO, 30));

      assertThat(line.discountCents()).isZero();
      assertThat(line.promotion()).isNull();
    }
  }

  @Nested
  class MultiBuy {

    private final PromotionRule threeForThree = new PromotionRule.MultiBuy(PROMO, 3, 300);

    @ParameterizedTest(name = "{0} units -> total {1}")
    @CsvSource({
      "1, 129", //  no complete group: full price, no discount
      "2, 258",
      "3, 300", //  one group
      "4, 429", //  one group + 1 remainder at full price
      "5, 558", //  one group + 2 remainder
      "6, 600", //  two groups
      "7, 729"
    })
    void completeGroupsCostBundlePriceRemainderFullPrice(int quantity, long expectedTotal) {
      PricedLine line = priceOne(129, quantity, threeForThree);

      assertThat(line.subtotalCents()).isEqualTo(129L * quantity);
      assertThat(line.totalCents()).isEqualTo(expectedTotal);
      assertThat(line.discountCents()).isEqualTo(129L * quantity - expectedTotal);
    }

    @Test
    void badgeOnlyWhenTheBundleApplies() {
      assertThat(priceOne(129, 2, threeForThree).promotion()).isNull();
      assertThat(priceOne(129, 3, threeForThree).promotion()).isEqualTo(threeForThree);
    }

    @Test
    void neverIncreasesThePrice() {
      // Bundle more expensive than buying units individually: no discount, no surcharge.
      PricedLine line = priceOne(100, 2, new PromotionRule.MultiBuy(PROMO, 2, 250));

      assertThat(line.discountCents()).isZero();
      assertThat(line.totalCents()).isEqualTo(200);
      assertThat(line.promotion()).isNull();
    }

    @Test
    void freeBundleDiscountsTheWholeGroup() {
      PricedLine line = priceOne(100, 3, new PromotionRule.MultiBuy(PROMO, 2, 0));

      assertThat(line.discountCents()).isEqualTo(200);
      assertThat(line.totalCents()).isEqualTo(100);
    }
  }

  @Test
  void totalsAggregateAllLinesAndAddTheDeliveryFee() {
    UUID other = UUID.fromString("00000000-0000-4000-8000-000000000002");
    UUID plain = UUID.fromString("00000000-0000-4000-8000-000000000003");
    Result result =
        PricingEngine.price(
            List.of(
                new Line(PRODUCT, 249, 2, new PromotionRule.PercentOff(PROMO, 20)), // 498 - 100
                new Line(other, 129, 4, new PromotionRule.MultiBuy(PROMO, 3, 300)), // 516 - 87
                new Line(plain, 119, 1, null)), // 119
            99);

    assertThat(result.itemCount()).isEqualTo(7);
    assertThat(result.subtotalCents()).isEqualTo(498 + 516 + 119);
    assertThat(result.discountCents()).isEqualTo(100 + 87);
    assertThat(result.merchandiseTotalCents()).isEqualTo(1133 - 187);
    assertThat(result.deliveryFeeCents()).isEqualTo(99);
    assertThat(result.totalCents()).isEqualTo(1133 - 187 + 99);
  }

  @Test
  void rejectsInvalidInput() {
    assertThatThrownBy(() -> new Line(PRODUCT, -1, 1, null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Line(PRODUCT, 1, -1, null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PricingEngine.price(List.of(), -1)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PromotionRule.MultiBuy(PROMO, 1, 100)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PromotionRule.PercentOff(PROMO, 0)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void labels() {
    assertThat(new PromotionRule.PercentOff(PROMO, 20).label()).isEqualTo("-20%");
    assertThat(new PromotionRule.MultiBuy(PROMO, 3, 300).label()).isEqualTo("3 for 3.00 €");
    assertThat(new PromotionRule.MultiBuy(PROMO, 2, 499).label()).isEqualTo("2 for 4.99 €");
    assertThat(new PromotionRule.MultiBuy(PROMO, List.of(new PromotionRule.Tier(7, 1000), new PromotionRule.Tier(3, 500))).label())
        .isEqualTo("3 for 5.00 € · 7 for 10.00 €");
    assertThat(new PromotionRule.FixedPrice(PROMO, 200).label()).isEqualTo("2.00 €");
  }

  @Nested
  class QuantityTiers {

    private final PromotionRule tiers = new PromotionRule.MultiBuy(PROMO, List.of(new PromotionRule.Tier(3, 500), new PromotionRule.Tier(7, 1000)));

    /** 2,00 € each, "3 for 5 €" and "7 for 10 €": always the cheapest combination. */
    @ParameterizedTest
    @CsvSource({"1, 200", "2, 400", "3, 500", "4, 700", "5, 900", "6, 1000", "7, 1000", "8, 1200", "9, 1400", "10, 1500", "13, 2000", "14, 2000"})
    void takeTheCheapestCombination(int quantity, long total) {
      assertThat(priceOne(200, quantity, tiers).totalCents()).isEqualTo(total);
    }

    @Test
    void aTierThatCostsMoreThanSingleUnitsIsIgnored() {
      // 1,00 € each but "3 for 5 €": three single units are cheaper.
      PricedLine line = priceOne(100, 3, tiers);
      assertThat(line.totalCents()).isEqualTo(300);
      assertThat(line.promotion()).isNull();
    }
  }

  @Nested
  class OfferPrice {

    @Test
    void everyUnitCostsTheOfferPrice() {
      PricedLine line = priceOne(500, 3, new PromotionRule.FixedPrice(PROMO, 200));
      assertThat(line.subtotalCents()).isEqualTo(1500);
      assertThat(line.discountCents()).isEqualTo(900);
      assertThat(line.totalCents()).isEqualTo(600);
    }

    @Test
    void anOfferAboveTheRegularPriceChangesNothing() {
      PricedLine line = priceOne(150, 2, new PromotionRule.FixedPrice(PROMO, 200));
      assertThat(line.totalCents()).isEqualTo(300);
      assertThat(line.promotion()).isNull();
    }
  }
}
