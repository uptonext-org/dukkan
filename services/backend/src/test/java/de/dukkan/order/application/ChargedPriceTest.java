package de.dukkan.order.application;

import static org.assertj.core.api.Assertions.assertThat;

import de.dukkan.order.domain.OrderItem.AppliedPromotion;
import org.junit.jupiter.api.Test;

class ChargedPriceTest {

  @Test
  void multiBuyIsRepricedForTheUnitsHandedOver() {
    // "3 for 5,00 €" on a 2,00 € item, 4 ordered: 5,00 + 2,00 = 7,00 €.
    AppliedPromotion threeForFive = new AppliedPromotion(null, 3, 500L);
    assertThat(ChargedPrice.of(200, 4, 700, threeForFive, 3)).isEqualTo(500);
    assertThat(ChargedPrice.of(200, 4, 700, threeForFive, 2)).isEqualTo(350);
    assertThat(ChargedPrice.of(200, 4, 700, threeForFive, 0)).isZero();
    assertThat(ChargedPrice.of(200, 4, 700, threeForFive, 4)).isEqualTo(700);
  }

  @Test
  void percentOffAndPlainLinesScaleDown() {
    // -20 % on 2,49 €: 199 per unit, 3 ordered = 597.
    assertThat(ChargedPrice.of(249, 3, 597, new AppliedPromotion(20, null, null), 1)).isEqualTo(199);
    assertThat(ChargedPrice.of(650, 3, 1950, AppliedPromotion.NONE, 1)).isEqualTo(650);
  }
}
