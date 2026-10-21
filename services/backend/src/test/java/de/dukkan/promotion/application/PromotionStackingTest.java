package de.dukkan.promotion.application;

import static org.assertj.core.api.Assertions.assertThat;

import de.dukkan.promotion.infrastructure.PromotionRepository.RuleRow;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PromotionStackingTest {

  @Test
  void mostRecentlyCreatedPromotionWinsPerListing() {
    UUID shared = UUID.randomUUID();
    UUID onlyOld = UUID.randomUUID();
    UUID newer = UUID.randomUUID();
    UUID older = UUID.randomUUID();
    // Rows come newest promotion first (ORDER BY created_at DESC).
    List<RuleRow> rows =
        List.of(
            new RuleRow(newer, shared, "PERCENT_OFF", 30, null, null),
            new RuleRow(older, shared, "MULTI_BUY", null, 3, 500L),
            new RuleRow(older, onlyOld, "MULTI_BUY", null, 3, 500L));

    Map<UUID, PromotionRule> winners = PromotionService.winnersByListing(rows);

    assertThat(winners)
        .hasSize(2)
        .containsEntry(shared, new PromotionRule.PercentOff(newer, 30))
        .containsEntry(onlyOld, new PromotionRule.MultiBuy(older, 3, 500));
  }

  @Test
  void eurosAreFormattedTheGermanWay() {
    assertThat(PromotionService.formatEuro(500)).isEqualTo("5,00 €");
    assertThat(PromotionService.formatEuro(123456)).isEqualTo("1.234,56 €");
  }
}
