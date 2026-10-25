package de.dukkan.order.application;

import static org.assertj.core.api.Assertions.assertThat;

import de.dukkan.order.domain.OrderItem.AppliedPromotion;
import org.junit.jupiter.api.Test;

class ChargedPriceTest {

  @Test
  void multiBuyIsRepricedForTheUnitsHandedOver() {
    // "3 for 5,00 €" on a 2,00 € item, 4 ordered: 5,00 + 2,00 = 7,00 €.
    AppliedPromotion threeForFive = new AppliedPromotion(null, 3, 500L, null, null);
    assertThat(ChargedPrice.of(200, 4, 700, threeForFive, 3)).isEqualTo(500);
    assertThat(ChargedPrice.of(200, 4, 700, threeForFive, 2)).isEqualTo(350);
    assertThat(ChargedPrice.of(200, 4, 700, threeForFive, 0)).isZero();
    assertThat(ChargedPrice.of(200, 4, 700, threeForFive, 4)).isEqualTo(700);
  }

  @Test
  void percentOffAndPlainLinesScaleDown() {
    // -20 % on 2,49 €: 199 per unit, 3 ordered = 597.
    assertThat(ChargedPrice.of(249, 3, 597, new AppliedPromotion(20, null, null, null, null), 1)).isEqualTo(199);
    assertThat(ChargedPrice.of(650, 3, 1950, AppliedPromotion.NONE, 1)).isEqualTo(650);
  }

  @Test
  void quantityTiersAndOfferPricesAreRepricedFromTheSnapshot() {
    // 2,00 € each, "3 for 5 €" and "7 for 10 €": 7 ordered = 10 €; 6 handed over = 2 × 5 € = 10 €,
    // capped by the pro-rata share 10 € × 6/7 = 8,57 €.
    AppliedPromotion tiers = new AppliedPromotion(null, 3, 500L, null, "3:500;7:1000");
    assertThat(ChargedPrice.of(200, 7, 1000, tiers, 6)).isEqualTo(857);
    assertThat(ChargedPrice.of(200, 7, 1000, tiers, 3)).isEqualTo(428);
    assertThat(ChargedPrice.of(200, 7, 1000, tiers, 1)).isEqualTo(142);
    // Offer price 2,00 € instead of 5,00 €: 3 ordered = 6 €, 2 handed over = 4 €.
    assertThat(ChargedPrice.of(500, 3, 600, new AppliedPromotion(null, null, null, 200L, null), 2)).isEqualTo(400);
  }
}
