package de.dukkan.promotion.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Active promotions at the current time. */
public interface PromotionService {

  /** Promotions active now, most recently created first. */
  List<PromotionView> listActive();

  /**
   * The single winning promotion per product (most recently created wins), for the given products.
   */
  Map<UUID, PromotionRule> winningRules(Collection<UUID> productIds);

  /** Ids of all products that currently have an active promotion. */
  Set<UUID> promotedProductIds();
}
