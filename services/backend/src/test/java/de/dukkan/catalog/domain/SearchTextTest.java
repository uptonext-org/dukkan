package de.dukkan.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchTextTest {

  @Test
  void combinesNormalisedNamesBrandAndKeywordsWithoutDuplicates() {
    String text = SearchText.build(List.of("Äpfel Elstar", "Elstar Apples", "تفاح إلستار"), "Hof Lindenmarsch", List.of("apfel", "elma"));

    assertThat(text).isEqualTo("apfel elstar | elstar apples | تفاح الستار | hof lindenmarsch | apfel | elma");
  }

  @Test
  void comparisonPriceIsPerReferenceUnitRoundedHalfUp() {
    assertThat(ComparisonPrice.of(249, new BigDecimal("0.5"), "kg")).contains(new ComparisonPrice(498, "kg"));
    assertThat(ComparisonPrice.of(199, new BigDecimal("6"), "piece")).contains(new ComparisonPrice(33, "piece"));
    assertThat(ComparisonPrice.of(100, null, null)).isEmpty();
  }
}
