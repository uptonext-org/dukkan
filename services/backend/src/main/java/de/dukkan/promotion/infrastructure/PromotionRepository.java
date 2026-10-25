package de.dukkan.promotion.infrastructure;

import de.dukkan.promotion.application.PromotionDraft;
import de.dukkan.promotion.application.PromotionRule;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PromotionRepository {

  /** One (promotion, listing) pair of a running promotion; tiers only for MULTI_BUY. */
  public record RuleRow(
      UUID promotionId,
      UUID listingId,
      String type,
      @Nullable Integer percentOff,
      @Nullable Long offerPriceCents,
      List<PromotionRule.Tier> tiers) {}

  /** A promotion with all translations, tiers and listings (with their offer prices). */
  public record PromotionRow(
      UUID id,
      UUID marketId,
      String type,
      @Nullable Integer percentOff,
      Instant validFrom,
      Instant validUntil,
      @Nullable List<Integer> weekdays,
      @Nullable LocalTime dailyFrom,
      @Nullable LocalTime dailyUntil,
      Map<String, String> titles,
      Map<String, String> descriptions,
      List<PromotionRule.Tier> tiers,
      Map<UUID, @Nullable Long> listings) {}

  /** Running at {@code :now} (SQL function from V23: dates, weekdays, daily window). */
  private static final String RUNNING = "promotion_running(p.valid_from, p.valid_until, p.weekdays, p.daily_from, p.daily_until, :now)";

  private final JdbcClient jdbc;

  public PromotionRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Running (promotion, listing) pairs for the listings, newest promotion first. */
  public List<RuleRow> findActiveRules(Set<UUID> listingIds, Instant now) {
    record Raw(UUID promotionId, UUID listingId, String type, @Nullable Integer percentOff, @Nullable Long offerPriceCents) {}
    List<Raw> raw =
        jdbc.sql(
                "SELECT p.id, pp.market_product_id, p.type, p.percent_off, pp.offer_price_cents"
                    + " FROM promotion p JOIN promotion_product pp ON pp.promotion_id = p.id"
                    + " WHERE pp.market_product_id IN (:ids) AND " + RUNNING
                    + " ORDER BY p.created_at DESC, p.id")
            .param("ids", List.copyOf(listingIds))
            .param("now", Timestamp.from(now))
            .query(
                (rs, i) ->
                    new Raw(
                        rs.getObject(1, UUID.class),
                        rs.getObject(2, UUID.class),
                        rs.getString(3),
                        rs.getObject(4, Integer.class),
                        rs.getObject(5, Long.class)))
            .list();
    Map<UUID, List<PromotionRule.Tier>> tiers =
        tiers(raw.stream().filter(r -> r.type().equals("MULTI_BUY")).map(Raw::promotionId).distinct().toList());
    return raw.stream()
        .map(r -> new RuleRow(r.promotionId(), r.listingId(), r.type(), r.percentOff(), r.offerPriceCents(), tiers.getOrDefault(r.promotionId(), List.of())))
        .toList();
  }

  public Set<UUID> findActiveListingIds(UUID marketId, Instant now) {
    return new HashSet<>(
        jdbc.sql(
                "SELECT DISTINCT pp.market_product_id FROM promotion p JOIN promotion_product pp ON pp.promotion_id = p.id"
                    + " WHERE p.market_id = :marketId AND " + RUNNING)
            .param("marketId", marketId)
            .param("now", Timestamp.from(now))
            .query(UUID.class)
            .list());
  }

  public Map<UUID, Integer> countActive(Set<UUID> marketIds, Instant now) {
    Map<UUID, Integer> counts = new HashMap<>();
    jdbc.sql("SELECT p.market_id, count(*) FROM promotion p WHERE p.market_id IN (:ids) AND " + RUNNING + " GROUP BY p.market_id")
        .param("ids", List.copyOf(marketIds))
        .param("now", Timestamp.from(now))
        .query(rs -> {
          counts.put(rs.getObject(1, UUID.class), rs.getInt(2));
        });
    return counts;
  }

  public Set<UUID> findMarketsWithActive(Instant now) {
    return new HashSet<>(
        jdbc.sql("SELECT DISTINCT p.market_id FROM promotion p WHERE " + RUNNING)
            .param("now", Timestamp.from(now))
            .query(UUID.class)
            .list());
  }

  /** Promotions of a market, newest first; {@code activeOnly} restricts to those running at {@code now}. */
  public List<PromotionRow> findByMarket(UUID marketId, Instant now, boolean activeOnly) {
    Map<UUID, PromotionRow> rows = new LinkedHashMap<>();
    jdbc.sql(
            "SELECT p.id, p.market_id, p.type, p.percent_off, p.valid_from, p.valid_until, p.weekdays, p.daily_from, p.daily_until"
                + " FROM promotion p WHERE p.market_id = :marketId"
                + (activeOnly ? " AND " + RUNNING : "")
                + " ORDER BY p.created_at DESC, p.id")
        .param("marketId", marketId)
        .param("now", Timestamp.from(now))
        .query(rs -> {
          PromotionRow row = row(rs);
          rows.put(row.id(), row);
        });
    if (rows.isEmpty()) {
      return List.of();
    }
    List<UUID> ids = List.copyOf(rows.keySet());
    jdbc.sql("SELECT promotion_id, locale, title, description FROM promotion_translation WHERE promotion_id IN (:ids)")
        .param("ids", ids)
        .query(rs -> {
          PromotionRow row = rows.get(rs.getObject(1, UUID.class));
          row.titles().put(rs.getString(2), rs.getString(3));
          String description = rs.getString(4);
          if (description != null) {
            row.descriptions().put(rs.getString(2), description);
          }
        });
    jdbc.sql(
            "SELECT promotion_id, market_product_id, offer_price_cents FROM promotion_product WHERE promotion_id IN (:ids)"
                + " ORDER BY market_product_id")
        .param("ids", ids)
        .query(rs -> {
          rows.get(rs.getObject(1, UUID.class)).listings().put(rs.getObject(2, UUID.class), rs.getObject(3, Long.class));
        });
    tiers(ids).forEach((id, tiers) -> rows.get(id).tiers().addAll(tiers));
    return List.copyOf(rows.values());
  }

  public void insert(UUID id, UUID marketId, PromotionDraft draft, Instant now) {
    PromotionRule.Tier smallest = draft.tiers().stream().min((a, b) -> Integer.compare(a.quantity(), b.quantity())).orElse(null);
    jdbc.sql(
            """
            INSERT INTO promotion (id, market_id, type, percent_off, bundle_quantity, bundle_price_cents,
                                   valid_from, valid_until, weekdays, daily_from, daily_until, created_at, updated_at)
            VALUES (:id, :marketId, :type, :percentOff, :bundleQuantity, :bundlePrice, :validFrom, :validUntil,
                    CAST(:weekdays AS smallint[]), :dailyFrom, :dailyUntil, :now, :now)
            """)
        .param("id", id)
        .param("marketId", marketId)
        .param("type", draft.type())
        .param("percentOff", draft.percentOff(), Types.INTEGER)
        .param("bundleQuantity", smallest == null ? null : smallest.quantity(), Types.INTEGER)
        .param("bundlePrice", smallest == null ? null : smallest.priceCents(), Types.BIGINT)
        .param("validFrom", Timestamp.from(draft.validFrom()))
        .param("validUntil", Timestamp.from(draft.validUntil()))
        .param("weekdays", draft.weekdays() == null ? null : "{" + String.join(",", draft.weekdays().stream().sorted().distinct().map(String::valueOf).toList()) + "}", Types.VARCHAR)
        .param("dailyFrom", draft.dailyFrom() == null ? null : Time.valueOf(draft.dailyFrom()), Types.TIME)
        .param("dailyUntil", draft.dailyUntil() == null ? null : Time.valueOf(draft.dailyUntil()), Types.TIME)
        .param("now", Timestamp.from(now))
        .update();
    for (Map.Entry<String, String> title : draft.title().entrySet()) {
      jdbc.sql(
              "INSERT INTO promotion_translation (promotion_id, locale, title, description) VALUES (:id, :locale, :title, :description)")
          .param("id", id)
          .param("locale", title.getKey())
          .param("title", title.getValue())
          .param("description", draft.description().get(title.getKey()), Types.VARCHAR)
          .update();
    }
    for (UUID listingId : Set.copyOf(draft.listingIds())) {
      jdbc.sql("INSERT INTO promotion_product (promotion_id, market_product_id, offer_price_cents) VALUES (:id, :listingId, :offer)")
          .param("id", id)
          .param("listingId", listingId)
          .param("offer", draft.offerPrices().get(listingId), Types.BIGINT)
          .update();
    }
    for (PromotionRule.Tier tier : draft.tiers()) {
      jdbc.sql("INSERT INTO promotion_tier (promotion_id, quantity, price_cents) VALUES (:id, :quantity, :price)")
          .param("id", id)
          .param("quantity", tier.quantity())
          .param("price", tier.priceCents())
          .update();
    }
  }

  /** Sets {@code valid_until} to now (and {@code valid_from} if it lies in the future). */
  public int end(UUID marketId, UUID promotionId, Instant now) {
    return jdbc.sql(
            """
            UPDATE promotion SET valid_until = LEAST(valid_until, :now), valid_from = LEAST(valid_from, :now), updated_at = :now
             WHERE id = :id AND market_id = :marketId
            """)
        .param("id", promotionId)
        .param("marketId", marketId)
        .param("now", Timestamp.from(now))
        .update();
  }

  private Map<UUID, List<PromotionRule.Tier>> tiers(List<UUID> promotionIds) {
    Map<UUID, List<PromotionRule.Tier>> tiers = new HashMap<>();
    if (promotionIds.isEmpty()) {
      return tiers;
    }
    jdbc.sql("SELECT promotion_id, quantity, price_cents FROM promotion_tier WHERE promotion_id IN (:ids) ORDER BY quantity")
        .param("ids", promotionIds)
        .query(rs -> {
          tiers.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>()).add(new PromotionRule.Tier(rs.getInt(2), rs.getLong(3)));
        });
    return tiers;
  }

  private static PromotionRow row(ResultSet rs) throws SQLException {
    Time from = rs.getTime("daily_from");
    Time until = rs.getTime("daily_until");
    return new PromotionRow(
        rs.getObject("id", UUID.class),
        rs.getObject("market_id", UUID.class),
        rs.getString("type"),
        rs.getObject("percent_off", Integer.class),
        rs.getTimestamp("valid_from").toInstant(),
        rs.getTimestamp("valid_until").toInstant(),
        weekdays(rs.getArray("weekdays")),
        from == null ? null : from.toLocalTime(),
        until == null ? null : until.toLocalTime(),
        new HashMap<>(),
        new HashMap<>(),
        new ArrayList<>(),
        new LinkedHashMap<>());
  }

  private static @Nullable List<Integer> weekdays(@Nullable Array array) throws SQLException {
    if (array == null) {
      return null;
    }
    return Arrays.stream((Object[]) array.getArray()).map(v -> ((Number) v).intValue()).sorted().toList();
  }
}
