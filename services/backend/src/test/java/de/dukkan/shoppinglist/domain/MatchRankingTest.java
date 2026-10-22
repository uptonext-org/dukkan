package de.dukkan.shoppinglist.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MatchRankingTest {

  @Test
  void prefersTheThingItselfOverCompoundsThatOnlyStartWithIt() {
    assertThat(MatchRanking.score("Eier", "Eier aus Freilandhaltung")).isZero();
    assertThat(MatchRanking.score("Tomaten", "Strauchtomaten")).isEqualTo(1);
    assertThat(MatchRanking.score("Gurke", "Salatgurke")).isEqualTo(1);
    assertThat(MatchRanking.score("Tomaten", "Tomatenmark")).isEqualTo(2);
    assertThat(MatchRanking.score("Eier", "Erişte-Eiernudeln")).isEqualTo(2);
    assertThat(MatchRanking.score("Milch", "Ayran")).isEqualTo(3);
    assertThat(MatchRanking.score("حليب", "حليب طازج")).isZero();
  }
}
