package de.dukkan.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ComparisonPriceTest {

  @Test
  void pricePerKilogramRoundsHalfUp() {
    assertThat(ComparisonPrice.of(349, new BigDecimal("0.500"), "kg")).contains(new ComparisonPrice(698, "kg"));
    assertThat(ComparisonPrice.of(279, new BigDecimal("0.150"), "kg")).contains(new ComparisonPrice(1860, "kg"));
    assertThat(ComparisonPrice.of(299, new BigDecimal("9.000"), "l")).contains(new ComparisonPrice(33, "l"));
    assertThat(ComparisonPrice.of(329, new BigDecimal("10"), "piece")).contains(new ComparisonPrice(33, "piece"));
  }

  @Test
  void absentWithoutNetQuantity() {
    assertThat(ComparisonPrice.of(100, null, null)).isEmpty();
    assertThat(ComparisonPrice.of(100, BigDecimal.ZERO, "kg")).isEmpty();
  }
}
