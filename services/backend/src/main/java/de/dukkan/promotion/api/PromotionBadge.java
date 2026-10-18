package de.dukkan.promotion.api;

import de.dukkan.promotion.application.PromotionRule;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Contract schema {@code PromotionBadge}; reused by catalog and cart APIs. */
public record PromotionBadge(UUID promotionId, String label, String type) {

  public static @Nullable PromotionBadge from(@Nullable PromotionRule rule) {
    return rule == null ? null : new PromotionBadge(rule.promotionId(), rule.label(), rule.type());
  }
}
