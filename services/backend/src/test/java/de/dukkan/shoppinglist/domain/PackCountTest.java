package de.dukkan.shoppinglist.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PackCountTest {

  private static Amount amount(String value, String unit) {
    return Amount.of(new BigDecimal(value), unit);
  }

  @Test
  void coversTheAmountWithWholePacks() {
    assertThat(PackCount.of(amount("1", "kg"), "500 g", 20)).isEqualTo(2);
    assertThat(PackCount.of(amount("750", "g"), "500 g", 20)).isEqualTo(2);
    assertThat(PackCount.of(amount("8", "piece"), "10 Stück", 20)).isEqualTo(1);
    assertThat(PackCount.of(amount("12", "piece"), "6 Stück", 20)).isEqualTo(2);
    assertThat(PackCount.of(amount("2", "piece"), "1 Stück", 20)).isEqualTo(2);
    assertThat(PackCount.of(amount("1.5", "l"), "1 l", 20)).isEqualTo(2);
    assertThat(PackCount.of(amount("1", "l"), "2 x 250 ml", 20)).isEqualTo(2);
  }

  @Test
  void takesCountsAsIsAndWeightsWithoutComparableSizeAsOnePack() {
    assertThat(PackCount.of(amount("3", "pack"), "500 g", 20)).isEqualTo(3);
    assertThat(PackCount.of(amount("3", "piece"), "500 g", 20)).isEqualTo(3);
    assertThat(PackCount.of(amount("2", "kg"), "1 Stück", 20)).isEqualTo(1);
    assertThat(PackCount.of(amount("2", "kg"), null, 20)).isEqualTo(1);
  }

  @Test
  void staysWithinTheOrderLimit() {
    assertThat(PackCount.of(amount("50", "piece"), "1 Stück", 12)).isEqualTo(12);
    assertThat(PackCount.of(amount("0.1", "g"), "1 kg", 12)).isEqualTo(1);
  }

  @Test
  void labelsWeightsAndVolumes() {
    assertThat(amount("1", "kg").label()).isEqualTo("1 kg");
    assertThat(amount("250", "g").label()).isEqualTo("250 g");
    assertThat(amount("0.5", "l").label()).isEqualTo("500 ml");
    assertThat(amount("2", "piece").label()).isNull();
  }
}
