package de.dukkan.order.infrastructure;

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

  public record MarketActivity(UUID marketId, Instant lastOrderAt, UUID lastOrderId, int orderCount) {}

  /** Markets the user ordered from (counted orders), most recent first. */
  public List<MarketActivity> orderedMarkets(UUID userId, int limit) {
    return jdbc.sql(
            """
            SELECT market_id, max(placed_at) AS last_order_at, (array_agg(id ORDER BY placed_at DESC))[1] AS last_order_id, count(*) AS orders
              FROM customer_order
             WHERE user_id = :userId AND status NOT IN ('CANCELLED', 'REJECTED')
             GROUP BY market_id
             ORDER BY last_order_at DESC
             LIMIT :limit
            """)
        .param("userId", userId)
        .param("limit", limit)
        .query(
            (rs, n) ->
                new MarketActivity(
                    rs.getObject("market_id", UUID.class),
                    rs.getTimestamp("last_order_at").toInstant(),
                    rs.getObject("last_order_id", UUID.class),
                    rs.getInt("orders")))
        .list();
  }

  /** Listings the user bought in the market, most recently bought first. */
  public List<UUID> recentlyBoughtListings(UUID userId, UUID marketId, int limit) {
    return jdbc.sql(
            """
            SELECT oi.market_product_id
              FROM order_item oi JOIN customer_order o ON o.id = oi.order_id
             WHERE o.user_id = :userId AND o.market_id = :marketId AND o.status NOT IN ('CANCELLED', 'REJECTED')
             GROUP BY oi.market_product_id
             ORDER BY max(o.placed_at) DESC, sum(oi.quantity) DESC, oi.market_product_id
             LIMIT :limit
            """)
        .param("userId", userId)
        .param("marketId", marketId)
        .param("limit", limit)
        .query(UUID.class)
        .list();
  }
}
