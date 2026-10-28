package de.dukkan.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RestrictedGoodsTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Bier", "Efes Pilsener Bier 0,5 l", "Weißbier", "Rotwein trocken", "Yeni Rakı", "Raki 70 cl", "Wodka", "Whisky",
        "Zigaretten", "Marlboro Zigarette", "Pfeifentabak", "Shisha-Tabak Doppelapfel", "Al Fakher معسل", "سجائر", "بيرة",
        "Tekel Birası", "Kırmızı şarap", "Sigara", "Nikotin Liquid", "Gin Tonic"
      })
  void alcoholAndTobaccoAreSuspected(String name) {
    assertThat(RestrictedGoods.suspected(name)).as(name).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Malzbier", "Alkoholfreies Bier", "Bier alkoholfrei", "Efes Malt alkolsüz", "Weinblätter gefüllt", "Weintrauben hell",
        "Weinessig", "Rumkugeln", "Ingwer", "Ginger Ale", "Kornflakes", "Rote Linsen", "Aleppo-Pfeffer", "Bierschinken",
        "Ayran", "Sucuk", "عدس أحمر", "Kırmızı mercimek", "Gingseng Tee"
      })
  void everydayGroceriesAreNot(String name) {
    assertThat(RestrictedGoods.suspected(name)).as(name).isFalse();
  }

  @Test
  void anyOfSeveralTextsAndNullsAreFine() {
    assertThat(RestrictedGoods.suspected(null, "Brot", null)).isFalse();
    assertThat(RestrictedGoods.suspected("Brot", "Raki")).isTrue();
  }
}
