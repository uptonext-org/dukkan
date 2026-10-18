package de.dukkan.promotion.api;

import de.dukkan.promotion.application.PromotionRule;
import de.dukkan.promotion.application.PromotionService;
import de.dukkan.promotion.application.PromotionView;
import de.dukkan.shared.api.Money;
import de.dukkan.shared.time.DukkanTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/v1")
@RestController
final class PromotionController {

  /** Contract schema {@code Promotion}. */
  record PromotionResponse(
      UUID id,
      String title,
      @Nullable String description,
      String type,
      @Nullable Integer percentOff,
      @Nullable Integer bundleQuantity,
      @Nullable Money bundlePrice,
      OffsetDateTime validFrom,
      OffsetDateTime validUntil,
      List<UUID> productIds) {

    static PromotionResponse from(PromotionView p) {
      Integer percentOff = null;
      Integer bundleQuantity = null;
      Money bundlePrice = null;
      switch (p.rule()) {
        case PromotionRule.PercentOff rule -> percentOff = rule.percentOff();
        case PromotionRule.MultiBuy rule -> {
          bundleQuantity = rule.bundleQuantity();
          bundlePrice = Money.eur(rule.bundlePriceCents());
        }
      }
      return new PromotionResponse(
          p.id(),
          p.title(),
          p.description(),
          p.rule().type(),
          percentOff,
          bundleQuantity,
          bundlePrice,
          DukkanTime.toApi(p.validFrom()),
          DukkanTime.toApi(p.validUntil()),
          p.productIds());
    }
  }

  private final PromotionService promotions;

  PromotionController(PromotionService promotions) {
    this.promotions = promotions;
  }

  @GetMapping("/promotions")
  List<PromotionResponse> list() {
    return promotions.listActive().stream().map(PromotionResponse::from).toList();
  }
}
