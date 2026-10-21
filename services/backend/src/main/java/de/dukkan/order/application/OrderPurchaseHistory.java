package de.dukkan.order.application;

import de.dukkan.catalog.application.PurchaseHistory;
import de.dukkan.order.infrastructure.PurchaseStatistics;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Implements catalog's {@link PurchaseHistory} port: listings in ≥ 2 non-cancelled orders. */
@Component
public class OrderPurchaseHistory implements PurchaseHistory {

  static final int FREQUENT_MIN_ORDERS = 2;

  private final PurchaseStatistics statistics;

  public OrderPurchaseHistory(PurchaseStatistics statistics) {
    this.statistics = statistics;
  }

  @Override
  public List<UUID> frequentlyOrderedListingIds(UUID userId, UUID marketId) {
    return statistics.listingsOrderedAtLeast(userId, marketId, FREQUENT_MIN_ORDERS);
  }
}
