package de.dukkan.capture.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RuleBasedDraftParserTest {

  @Test
  void readsNameSizePriceAndCategoryFromAGermanNote() {
    ExtractedProduct product = RuleBasedDraftParser.parse("Sucuk Gazi 400 g 4,49 €", "de");

    assertThat(product.names()).containsExactlyEntriesOf(java.util.Map.of("de", "Sucuk Gazi"));
    assertThat(product.unitSize()).isEqualTo("400 g");
    assertThat(product.netQuantity()).isEqualByComparingTo(new BigDecimal("0.4"));
    assertThat(product.netUnit()).isEqualTo("kg");
    assertThat(product.priceCents()).isEqualTo(449L);
    assertThat(product.categorySlug()).isEqualTo("meat");
    assertThat(product.organic()).isFalse();
  }

  @Test
  void readsArabicIndicDigitsAndArabicUnits() {
    ExtractedProduct product = RuleBasedDraftParser.parse("عدس أحمر ١ كيلو ٢٫٩٩", "de");

    assertThat(product.names()).containsEntry("ar", "عدس أحمر").hasSize(1);
    assertThat(product.unitSize()).isEqualTo("1 kg");
    assertThat(product.netQuantity()).isEqualByComparingTo(BigDecimal.ONE);
    assertThat(product.priceCents()).isEqualTo(299L);
    assertThat(product.categorySlug()).isEqualTo("grains");
  }

  @Test
  void readsMultipacksPriceBeforeTheAmountAndOrganic() {
    ExtractedProduct product = RuleBasedDraftParser.parse("bio ayran 6x250ml € 3.5", "en");

    assertThat(product.names()).containsEntry("en", "Bio ayran");
    assertThat(product.unitSize()).isEqualTo("6 × 250 ml");
    assertThat(product.netQuantity()).isEqualByComparingTo(new BigDecimal("1.5"));
    assertThat(product.netUnit()).isEqualTo("l");
    assertThat(product.priceCents()).isEqualTo(350L);
    assertThat(product.organic()).isTrue();
    assertThat(product.categorySlug()).isEqualTo("dairy");
    assertThat(product.allergens()).containsExactly("milk");
  }

  @Test
  void decimalLitresPiecesAndBarePrices() {
    assertThat(RuleBasedDraftParser.parse("Olivenöl 1,5 l 12,90", "de"))
        .satisfies(
            p -> {
              assertThat(p.unitSize()).isEqualTo("1,5 l");
              assertThat(p.priceCents()).isEqualTo(1290L);
              assertThat(p.categorySlug()).isEqualTo("spices");
              assertThat(p.names()).containsEntry("de", "Olivenöl");
            });
    assertThat(RuleBasedDraftParser.parse("Simit 5 Stück", "de"))
        .satisfies(
            p -> {
              assertThat(p.unitSize()).isEqualTo("5 Stück");
              assertThat(p.netUnit()).isEqualTo("piece");
              assertThat(p.priceCents()).isNull();
              assertThat(p.categorySlug()).isEqualTo("bakery");
              assertThat(p.allergens()).containsExactly("gluten", "sesame");
            });
  }

  @Test
  void compoundWordsMatchKeywordsInsideThem() {
    assertThat(RuleBasedDraftParser.parse("Hähnchenbrustfilet", "de").categorySlug()).isEqualTo("meat");
    assertThat(RuleBasedDraftParser.parse("Paprikapulver edelsüß", "de").categorySlug()).isEqualTo("spices");
    assertThat(RuleBasedDraftParser.parse("الخبز العربي", "de").categorySlug()).isEqualTo("bakery");
    assertThat(RuleBasedDraftParser.parse("Türkischer Tee", "de").cuisineCodes()).containsExactly("TURKISH");
  }

  @Test
  void emptyOrUnknownInputGivesAnEmptyDraft() {
    assertThat(RuleBasedDraftParser.parse(null, "de")).isEqualTo(ExtractedProduct.empty());
    assertThat(RuleBasedDraftParser.parse("   ", "de")).isEqualTo(ExtractedProduct.empty());
    ExtractedProduct unknown = RuleBasedDraftParser.parse("Xyzzy", "de");
    assertThat(unknown.categorySlug()).isNull();
    assertThat(unknown.names()).containsEntry("de", "Xyzzy");
    assertThat(RuleBasedDraftParser.parse("4,99 €", "de").names()).isEmpty();
  }
}
