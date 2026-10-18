package de.dukkan.promotion.application;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toUnmodifiableSet;

import de.dukkan.promotion.domain.Promotion;
import de.dukkan.promotion.infrastructure.PromotionRepository;
import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class DefaultPromotionService implements PromotionService {

  private final PromotionRepository promotions;
  private final Clock clock;

  DefaultPromotionService(PromotionRepository promotions, Clock clock) {
    this.promotions = promotions;
    this.clock = clock;
  }

  @Override
  public List<PromotionView> listActive() {
    return active().stream().map(DefaultPromotionService::view).toList();
  }

  @Override
  public Map<UUID, PromotionRule> winningRules(Collection<UUID> productIds) {
    Set<UUID> wanted = Set.copyOf(productIds);
    Map<UUID, PromotionRule> winners = winnersByProduct(listActive());
    winners.keySet().retainAll(wanted);
    return winners;
  }

  @Override
  public Set<UUID> promotedProductIds() {
    return active().stream().flatMap(p -> p.getProductIds().stream()).collect(toUnmodifiableSet());
  }

  /**
   * Resolves overlaps: {@code newestFirst} must be ordered by creation time, newest first; the
   * first promotion seen for a product wins.
   */
  static Map<UUID, PromotionRule> winnersByProduct(List<PromotionView> newestFirst) {
    Map<UUID, PromotionRule> winners = new HashMap<>();
    for (PromotionView promotion : newestFirst) {
      for (UUID productId : promotion.productIds()) {
        winners.putIfAbsent(productId, promotion.rule());
      }
    }
    return winners;
  }

  private List<Promotion> active() {
    return promotions.findActive(clock.instant());
  }

  private static PromotionView view(Promotion p) {
    PromotionRule rule =
        switch (p.getType()) {
          case PERCENT_OFF -> new PromotionRule.PercentOff(p.getId(), requireNonNull(p.getPercentOff()));
          case MULTI_BUY ->
              new PromotionRule.MultiBuy(
                  p.getId(),
                  requireNonNull(p.getBundleQuantity()),
                  requireNonNull(p.getBundlePriceCents()));
        };
    return new PromotionView(
        p.getId(),
        p.getTitle(),
        p.getDescription(),
        rule,
        p.getValidFrom(),
        p.getValidUntil(),
        p.getProductIds().stream().sorted().toList());
  }
}
