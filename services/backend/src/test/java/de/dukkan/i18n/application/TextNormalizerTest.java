package de.dukkan.i18n.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TextNormalizerTest {

  @ParameterizedTest
  @CsvSource({
    "Äpfel, apfel",
    "ÄPFEL Elstar, apfel elstar",
    "Straße, strasse",
    "Möhren, mohren",
    "Çarliston Biber, carliston biber",
    "Kaşar, kasar",
    "masło, maslo",
    "Crème fraîche, creme fraiche",
    "'  Lange   Reihe  ', lange reihe",
  })
  void foldsGermanAndOtherLatinText(String input, String expected) {
    assertThat(TextNormalizer.normalize(input)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    // tashkeel (shadda, fatha) removed
    "تُفَّاح, تفاح",
    // alef with hamza above / below / madda → bare alef
    "أرز, ارز",
    "إسطنبول, اسطنبول",
    "آمن, امن",
    // alef maqsura → ya, ta marbuta → ha
    "مستشفى, مستشفي",
    "قهوة, قهوه",
    // tatweel removed
    "طــحينة, طحينه",
    // Arabic-Indic digits
    "٢٥٠ غ, 250 غ",
  })
  void normalisesArabic(String input, String expected) {
    assertThat(TextNormalizer.normalize(input)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({"تفاح, تُفّاح", "apfel, ÄPFEL", "قهوه, قَهْوَة"})
  void queryAndStoredTextNormaliseToTheSameValue(String query, String stored) {
    assertThat(TextNormalizer.normalize(query)).isEqualTo(TextNormalizer.normalize(stored));
  }

  @org.junit.jupiter.api.Test
  void handlesNullAndEscapesLikeWildcards() {
    assertThat(TextNormalizer.normalize(null)).isEmpty();
    assertThat(TextNormalizer.normalize("   ")).isEmpty();
    assertThat(TextNormalizer.containsPattern("50%_off\\")).isEqualTo("%50\\%\\_off\\\\%");
    assertThat(TextNormalizer.prefixPattern("apfel")).isEqualTo("apfel%");
  }
}
