package de.dukkan.promotion.infrastructure;

import de.dukkan.promotion.application.PromotionDraft;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
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

  /** One (promotion, listing) pair of an active promotion. */
  public record RuleRow(
      UUID promotionId,
      UUID listingId,
      String type,
      @Nullable Integer percentOff,
      @Nullable Integer bundleQuantity,
      @Nullable Long bundlePriceCents) {}

  /** A promotion with all translations and listing ids. */
  public record PromotionRow(
      UUID id,
      UUID marketId,
      String type,
      @Nullable Integer percentOff,
      @Nullable Integer bundleQuantity,
      @Nullable Long bundlePriceCents,
      Instant validFrom,
      Instant validUntil,
      Map<String, String> titles,
      Map<String, String> descriptions,
      List<UUID> listingIds) {}

  private final JdbcClient jdbc;

  public PromotionRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Active (promotion, listing) pairs for the listings, newest promotion first. */
  public List<RuleRow> findActiveRules(Set<UUID> listingIds, Instant now) {
    return jdbc.sql(
            """
            SELECT p.id, pp.market_product_id, p.type, p.percent_off, p.bundle_quantity, p.bundle_price_cents
              FROM promotion p JOIN promotion_product pp ON pp.promotion_id = p.id
             WHERE pp.market_product_id IN (:ids) AND p.valid_from <= :now AND p.valid_until > :now
             ORDER BY p.created_at DESC, p.id
            """)
        .param("ids", List.copyOf(listingIds))
        .param("now", Timestamp.from(now))
        .query(
            (rs, i) ->
                new RuleRow(
                    rs.getObject(1, UUID.class),
                    rs.getObject(2, UUID.class),
                    rs.getString(3),
                    rs.getObject(4, Integer.class),
                    rs.getObject(5, Integer.class),
                    rs.getObject(6, Long.class)))
        .list();
  }

  public Set<UUID> findActiveListingIds(UUID marketId, Instant now) {
    return new HashSet<>(
        jdbc.sql(
                """
                SELECT DISTINCT pp.market_product_id
                  FROM promotion p JOIN promotion_product pp ON pp.promotion_id = p.id
                 WHERE p.market_id = :marketId AND p.valid_from <= :now AND p.valid_until > :now
                """)
            .param("marketId", marketId)
            .param("now", Timestamp.from(now))
            .query(UUID.class)
            .list());
  }

  public Map<UUID, Integer> countActive(Set<UUID> marketIds, Instant now) {
    Map<UUID, Integer> counts = new HashMap<>();
    jdbc.sql(
            """
            SELECT market_id, count(*) FROM promotion
             WHERE market_id IN (:ids) AND valid_from <= :now AND valid_until > :now
             GROUP BY market_id
            """)
        .param("ids", List.copyOf(marketIds))
        .param("now", Timestamp.from(now))
        .query(rs -> {
          counts.put(rs.getObject(1, UUID.class), rs.getInt(2));
        });
    return counts;
  }

  public Set<UUID> findMarketsWithActive(Instant now) {
    return new HashSet<>(
        jdbc.sql("SELECT DISTINCT market_id FROM promotion WHERE valid_from <= :now AND valid_until > :now")
            .param("now", Timestamp.from(now))
            .query(UUID.class)
            .list());
  }

  /** Promotions of a market, newest first; {@code activeOnly} restricts to those active at {@code now}. */
  public List<PromotionRow> findByMarket(UUID marketId, Instant now, boolean activeOnly) {
    Map<UUID, PromotionRow> rows = new LinkedHashMap<>();
    jdbc.sql(
            "SELECT id, market_id, type, percent_off, bundle_quantity, bundle_price_cents, valid_from, valid_until"
                + " FROM promotion WHERE market_id = :marketId"
                + (activeOnly ? " AND valid_from <= :now AND valid_until > :now" : "")
                + " ORDER BY created_at DESC, id")
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
            "SELECT promotion_id, market_product_id FROM promotion_product WHERE promotion_id IN (:ids)"
                + " ORDER BY market_product_id")
        .param("ids", ids)
        .query(rs -> {
          rows.get(rs.getObject(1, UUID.class)).listingIds().add(rs.getObject(2, UUID.class));
        });
    return List.copyOf(rows.values());
  }

  public void insert(UUID id, UUID marketId, PromotionDraft draft, Instant now) {
    jdbc.sql(
            """
            INSERT INTO promotion (id, market_id, type, percent_off, bundle_quantity, bundle_price_cents,
                                   valid_from, valid_until, created_at, updated_at)
            VALUES (:id, :marketId, :type, :percentOff, :bundleQuantity, :bundlePrice, :validFrom, :validUntil, :now, :now)
            """)
        .param("id", id)
        .param("marketId", marketId)
        .param("type", draft.type())
        .param("percentOff", draft.percentOff(), java.sql.Types.INTEGER)
        .param("bundleQuantity", draft.bundleQuantity(), java.sql.Types.INTEGER)
        .param("bundlePrice", draft.bundlePriceCents(), java.sql.Types.BIGINT)
        .param("validFrom", Timestamp.from(draft.validFrom()))
        .param("validUntil", Timestamp.from(draft.validUntil()))
        .param("now", Timestamp.from(now))
        .update();
    for (Map.Entry<String, String> title : draft.title().entrySet()) {
      jdbc.sql(
              "INSERT INTO promotion_translation (promotion_id, locale, title, description) VALUES (:id, :locale, :title, :description)")
          .param("id", id)
          .param("locale", title.getKey())
          .param("title", title.getValue())
          .param("description", draft.description().get(title.getKey()), java.sql.Types.VARCHAR)
          .update();
    }
    for (UUID listingId : Set.copyOf(draft.listingIds())) {
      jdbc.sql("INSERT INTO promotion_product (promotion_id, market_product_id) VALUES (:id, :listingId)")
          .param("id", id)
          .param("listingId", listingId)
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

  private static PromotionRow row(ResultSet rs) throws SQLException {
    return new PromotionRow(
        rs.getObject("id", UUID.class),
        rs.getObject("market_id", UUID.class),
        rs.getString("type"),
        rs.getObject("percent_off", Integer.class),
        rs.getObject("bundle_quantity", Integer.class),
        rs.getObject("bundle_price_cents", Long.class),
        rs.getTimestamp("valid_from").toInstant(),
        rs.getTimestamp("valid_until").toInstant(),
        new HashMap<>(),
        new HashMap<>(),
        new ArrayList<>());
  }
}
