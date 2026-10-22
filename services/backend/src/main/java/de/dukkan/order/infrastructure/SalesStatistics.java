package de.dukkan.order.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Aggregates over a market's orders for the merchant's sales overview. */
@Repository
public class SalesStatistics {

  /** Orders that count as sales. */
  private static final String COUNTED = "o.status NOT IN ('CANCELLED', 'REJECTED')";

  private final JdbcClient jdbc;

  public SalesStatistics(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public record Summary(
      long orders,
      long revenueCents,
      long discountCents,
      long cancelled,
      long rejected,
      long pickup,
      long delivery,
      long customers,
      long returningCustomers) {}

  public record DayRow(LocalDate date, long orders, long revenueCents) {}

  public record ProductRow(UUID listingId, String name, long quantity, long revenueCents) {}

  /**
   * Totals of orders placed in {@code [from, to)}. A returning customer had already ordered in this
   * market before one of their orders in the period (also earlier in the same period).
   */
  public Summary summary(UUID marketId, Instant from, Instant to) {
    return jdbc.sql(
            """
            SELECT count(*) FILTER (WHERE %1$s) AS orders,
                   coalesce(sum(o.total_cents) FILTER (WHERE %1$s), 0) AS revenue,
                   coalesce(sum(o.discount_total_cents) FILTER (WHERE %1$s), 0) AS discount,
                   count(*) FILTER (WHERE o.status = 'CANCELLED') AS cancelled,
                   count(*) FILTER (WHERE o.status = 'REJECTED') AS rejected,
                   count(*) FILTER (WHERE %1$s AND o.fulfilment_type = 'PICKUP') AS pickup,
                   count(*) FILTER (WHERE %1$s AND o.fulfilment_type = 'DELIVERY') AS delivery,
                   count(DISTINCT o.user_id) FILTER (WHERE %1$s) AS customers,
                   count(DISTINCT o.user_id) FILTER (
                     WHERE %1$s AND EXISTS (
                       SELECT 1 FROM customer_order p
                        WHERE p.market_id = o.market_id AND p.user_id = o.user_id AND p.id <> o.id
                          AND p.placed_at < o.placed_at AND p.status NOT IN ('CANCELLED', 'REJECTED'))) AS returning
              FROM customer_order o
             WHERE o.market_id = :marketId AND o.placed_at >= :from AND o.placed_at < :to
            """
                .formatted(COUNTED))
        .param("marketId", marketId)
        .param("from", Timestamp.from(from))
        .param("to", Timestamp.from(to))
        .query(
            (rs, n) ->
                new Summary(
                    rs.getLong("orders"),
                    rs.getLong("revenue"),
                    rs.getLong("discount"),
                    rs.getLong("cancelled"),
                    rs.getLong("rejected"),
                    rs.getLong("pickup"),
                    rs.getLong("delivery"),
                    rs.getLong("customers"),
                    rs.getLong("returning")))
        .single();
  }

  /** Counted orders and revenue per local day (Europe/Berlin); days without orders are missing. */
  public List<DayRow> daily(UUID marketId, Instant from, Instant to) {
    return jdbc.sql(
            """
            SELECT (o.placed_at AT TIME ZONE 'Europe/Berlin')::date AS day, count(*) AS orders, sum(o.total_cents) AS revenue
              FROM customer_order o
             WHERE o.market_id = :marketId AND o.placed_at >= :from AND o.placed_at < :to AND %s
             GROUP BY day
             ORDER BY day
            """
                .formatted(COUNTED))
        .param("marketId", marketId)
        .param("from", Timestamp.from(from))
        .param("to", Timestamp.from(to))
        .query((rs, n) -> new DayRow(rs.getObject("day", LocalDate.class), rs.getLong("orders"), rs.getLong("revenue")))
        .list();
  }

  /** Counted orders per local hour of the day (index 0–23). */
  public long[] ordersByHour(UUID marketId, Instant from, Instant to) {
    long[] hours = new long[24];
    jdbc.sql(
            """
            SELECT extract(HOUR FROM o.placed_at AT TIME ZONE 'Europe/Berlin')::int AS hour, count(*) AS orders
              FROM customer_order o
             WHERE o.market_id = :marketId AND o.placed_at >= :from AND o.placed_at < :to AND %s
             GROUP BY hour
            """
                .formatted(COUNTED))
        .param("marketId", marketId)
        .param("from", Timestamp.from(from))
        .param("to", Timestamp.from(to))
        .query(rs -> {
          hours[rs.getInt("hour")] = rs.getLong("orders");
        });
    return hours;
  }

  /** Best-selling listings by quantity, then revenue; {@code name} is the newest order snapshot. */
  public List<ProductRow> topProducts(UUID marketId, Instant from, Instant to, int limit) {
    return jdbc.sql(
            """
            SELECT oi.market_product_id AS listing_id,
                   (array_agg(oi.name ORDER BY o.placed_at DESC))[1] AS name,
                   sum(oi.quantity) AS quantity,
                   sum(oi.line_total_cents) AS revenue
              FROM order_item oi JOIN customer_order o ON o.id = oi.order_id
             WHERE o.market_id = :marketId AND o.placed_at >= :from AND o.placed_at < :to AND %s
             GROUP BY oi.market_product_id
             ORDER BY quantity DESC, revenue DESC, listing_id
             LIMIT :limit
            """
                .formatted(COUNTED))
        .param("marketId", marketId)
        .param("from", Timestamp.from(from))
        .param("to", Timestamp.from(to))
        .param("limit", limit)
        .query((rs, n) -> new ProductRow(rs.getObject("listing_id", UUID.class), rs.getString("name"), rs.getLong("quantity"), rs.getLong("revenue")))
        .list();
  }
}
