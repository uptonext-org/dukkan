package de.dukkan.order.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Aggregates over a user's order lines. */
@Repository
public class PurchaseStatistics {

  private final JdbcClient jdbc;

  public PurchaseStatistics(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Listings that appear in at least {@code minOrders} non-cancelled orders, most frequent first. */
  public List<UUID> listingsOrderedAtLeast(UUID userId, UUID marketId, int minOrders) {
    return jdbc.sql(
            """
            SELECT oi.market_product_id
              FROM order_item oi JOIN customer_order o ON o.id = oi.order_id
             WHERE o.user_id = :userId AND o.market_id = :marketId AND o.status <> 'CANCELLED'
             GROUP BY oi.market_product_id
            HAVING count(DISTINCT o.id) >= :minOrders
             ORDER BY count(DISTINCT o.id) DESC, max(o.placed_at) DESC, oi.market_product_id
            """)
        .param("userId", userId)
        .param("marketId", marketId)
        .param("minOrders", minOrders)
        .query(UUID.class)
        .list();
  }

  /** Listings by units sold in the market's counted orders since {@code since}, most first. */
  public List<UUID> bestSellingListings(UUID marketId, Instant since, int limit) {
    return jdbc.sql(
            """
            SELECT oi.market_product_id
              FROM order_item oi JOIN customer_order o ON o.id = oi.order_id
             WHERE o.market_id = :marketId AND o.placed_at >= :since AND o.status NOT IN ('CANCELLED', 'REJECTED')
             GROUP BY oi.market_product_id
             ORDER BY sum(oi.quantity) DESC, count(DISTINCT o.id) DESC, oi.market_product_id
             LIMIT :limit
            """)
        .param("marketId", marketId)
        .param("since", Timestamp.from(since))
        .param("limit", limit)
        .query(UUID.class)
        .list();
  }
}
