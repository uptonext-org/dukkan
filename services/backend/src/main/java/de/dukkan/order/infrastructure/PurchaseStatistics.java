package de.dukkan.order.infrastructure;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

  /** Per market, the listings the user bought there, most recently bought first (one query). */
  public Map<UUID, List<UUID>> recentlyBoughtListings(UUID userId, Collection<UUID> marketIds, int perMarket) {
    if (marketIds.isEmpty()) {
      return Map.of();
    }
    Map<UUID, List<UUID>> out = new LinkedHashMap<>();
    jdbc.sql(
            """
            SELECT market_id, market_product_id FROM (
              SELECT o.market_id, oi.market_product_id,
                     row_number() OVER (PARTITION BY o.market_id
                                        ORDER BY max(o.placed_at) DESC, sum(oi.quantity) DESC, oi.market_product_id) AS rank
                FROM order_item oi JOIN customer_order o ON o.id = oi.order_id
               WHERE o.user_id = :userId AND o.market_id IN (:marketIds) AND o.status NOT IN ('CANCELLED', 'REJECTED')
               GROUP BY o.market_id, oi.market_product_id
            ) ranked
             WHERE rank <= :perMarket
             ORDER BY market_id, rank
            """)
        .param("userId", userId)
        .param("marketIds", marketIds)
        .param("perMarket", perMarket)
        .query((rs, n) -> Map.entry(rs.getObject("market_id", UUID.class), rs.getObject("market_product_id", UUID.class)))
        .list()
        .forEach(row -> out.computeIfAbsent(row.getKey(), k -> new ArrayList<>()).add(row.getValue()));
    return out;
  }
}
