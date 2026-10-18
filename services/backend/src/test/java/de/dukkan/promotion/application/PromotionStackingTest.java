package de.dukkan.promotion.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PromotionStackingTest {

  private static final Instant FROM = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant UNTIL = Instant.parse("2030-12-31T00:00:00Z");

  @Test
  void mostRecentlyCreatedPromotionWinsPerProduct() {
    UUID shared = UUID.randomUUID();
    UUID onlyOld = UUID.randomUUID();
    PromotionView newer =
        new PromotionView(
            UUID.randomUUID(), "new", null, new PromotionRule.PercentOff(UUID.randomUUID(), 30), FROM, UNTIL, List.of(shared));
    PromotionView older =
        new PromotionView(
            UUID.randomUUID(),
            "old",
            null,
            new PromotionRule.PercentOff(UUID.randomUUID(), 20),
            FROM,
            UNTIL,
            List.of(shared, onlyOld));

    Map<UUID, PromotionRule> winners = DefaultPromotionService.winnersByProduct(List.of(newer, older));

    assertThat(winners).containsEntry(shared, newer.rule()).containsEntry(onlyOld, older.rule()).hasSize(2);
  }
}
