package de.dukkan.market.infrastructure;

import de.dukkan.geo.application.GeoMath;
import de.dukkan.market.application.MarketProfile;
import de.dukkan.market.application.OpeningHours;
import de.dukkan.market.application.OpeningInterval;
import de.dukkan.shared.domain.FulfilmentType;
import de.dukkan.shared.persistence.SqlTextArray;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** SQL access to markets, their translations, cuisines, opening hours, owners and banners. */
@Repository
public class MarketRepository {

  /** All columns of a market plus its cuisines, opening hours and description translations. */
  public record MarketRow(
      UUID id,
      String slug,
      String name,
      String typeCode,
      String status,
      String primaryLocale,
      String addressLine,
      double lat,
      double lng,
      @Nullable String phone,
      @Nullable String logoUrl,
      @Nullable String coverImageUrl,
      List<String> tags,
      boolean delivery,
      boolean pickup,
      @Nullable Double deliveryRadiusKm,
      @Nullable Long deliveryFeeCents,
      @Nullable Long freeDeliveryFromCents,
      long minimumOrderCents,
      String legalName,
      @Nullable String registerInfo,
      List<String> cuisines,
      List<OpeningInterval> hours,
      Map<String, String> descriptions) {}

  /** Radius search filters (openNow and name search are applied in Java). */
  public record Search(
      double lat,
      double lng,
      double radiusMeters,
      List<String> types,
      List<String> cuisines,
      @Nullable FulfilmentType fulfilment,
      @Nullable Collection<UUID> onlyMarketIds,
      String sort) {}

  /** A banner row with all translations. */
  public record BannerRow(
      UUID id,
      String tint,
      @Nullable String imageUrl,
      String targetType,
      @Nullable UUID targetId,
      Map<String, String> overlines,
      Map<String, String> titles) {}

  /** Haversine distance in metres between the market and (:lat, :lng). */
  private static final String DISTANCE =
      "2 * " + GeoMath.EARTH_RADIUS_METERS + " * asin(least(1, sqrt("
          + "power(sin(radians(m.lat - :lat) / 2), 2)"
          + " + cos(radians(:lat)) * cos(radians(m.lat)) * power(sin(radians(m.lng - :lng) / 2), 2))))";

  private final JdbcClient jdbc;

  public MarketRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Active markets within the radius, ordered by the requested sort. A bounding box on the
   * indexed (lat, lng) columns prefilters before the exact distance is computed.
   */
  public List<Map.Entry<UUID, Double>> searchActive(Search search) {
    GeoMath.BoundingBox box = GeoMath.boundingBox(search.lat(), search.lng(), search.radiusMeters());
    StringBuilder sql =
        new StringBuilder(
            "SELECT m.id, d.distance FROM market m CROSS JOIN LATERAL (SELECT "
                + DISTANCE
                + " AS distance) d WHERE m.status = 'ACTIVE'"
                + " AND m.lat BETWEEN :minLat AND :maxLat AND m.lng BETWEEN :minLng AND :maxLng"
                + " AND d.distance <= :radius");
    if (!search.types().isEmpty()) {
      sql.append(" AND m.type_code IN (:types)");
    }
    if (!search.cuisines().isEmpty()) {
      sql.append(" AND EXISTS (SELECT 1 FROM market_cuisine mc WHERE mc.market_id = m.id AND mc.cuisine_code IN (:cuisines))");
    }
    if (search.fulfilment() == FulfilmentType.DELIVERY) {
      sql.append(" AND m.delivery");
    } else if (search.fulfilment() == FulfilmentType.PICKUP) {
      sql.append(" AND m.pickup");
    }
    if (search.onlyMarketIds() != null) {
      if (search.onlyMarketIds().isEmpty()) {
        return List.of();
      }
      sql.append(" AND m.id IN (:only)");
    }
    sql.append(" ORDER BY ")
        .append(
            switch (search.sort()) {
              case "name" -> "lower(m.name), d.distance";
              case "minimum_order" -> "m.minimum_order_cents, d.distance";
              case "delivery_fee" -> "m.delivery_fee_cents NULLS LAST, d.distance";
              default -> "d.distance, lower(m.name)";
            })
        .append(", m.id");
    JdbcClient.StatementSpec spec =
        jdbc.sql(sql.toString())
            .param("lat", search.lat())
            .param("lng", search.lng())
            .param("minLat", box.minLat())
            .param("maxLat", box.maxLat())
            .param("minLng", box.minLng())
            .param("maxLng", box.maxLng())
            .param("radius", search.radiusMeters());
    if (!search.types().isEmpty()) {
      spec = spec.param("types", search.types());
    }
    if (!search.cuisines().isEmpty()) {
      spec = spec.param("cuisines", search.cuisines());
    }
    if (search.onlyMarketIds() != null) {
      spec = spec.param("only", List.copyOf(search.onlyMarketIds()));
    }
    return spec.query((rs, i) -> Map.entry(rs.getObject(1, UUID.class), rs.getDouble(2))).list();
  }

  /** Markets by id (any status), keyed by id; unknown ids are absent. */
  public Map<UUID, MarketRow> findByIds(Collection<UUID> ids) {
    if (ids.isEmpty()) {
      return Map.of();
    }
    List<UUID> idList = List.copyOf(Set.copyOf(ids));
    Map<UUID, MarketRow> rows = new LinkedHashMap<>();
    jdbc.sql("SELECT * FROM market WHERE id IN (:ids)")
        .param("ids", idList)
        .query(rs -> {
          MarketRow row = row(rs);
          rows.put(row.id(), row);
        });
    if (rows.isEmpty()) {
      return rows;
    }
    jdbc.sql(
            "SELECT mc.market_id, mc.cuisine_code FROM market_cuisine mc JOIN cuisine c ON c.code = mc.cuisine_code"
                + " WHERE mc.market_id IN (:ids) ORDER BY c.sort_order")
        .param("ids", idList)
        .query(rs -> {
          rows.get(rs.getObject(1, UUID.class)).cuisines().add(rs.getString(2));
        });
    jdbc.sql(
            "SELECT market_id, day_of_week, opens_at, closes_at FROM market_opening_hours WHERE market_id IN (:ids)"
                + " ORDER BY day_of_week, opens_at")
        .param("ids", idList)
        .query(rs -> {
          rows.get(rs.getObject(1, UUID.class))
              .hours()
              .add(
                  new OpeningInterval(
                      rs.getInt(2), rs.getObject(3, LocalTime.class), rs.getObject(4, LocalTime.class)));
        });
    jdbc.sql("SELECT market_id, locale, description FROM market_translation WHERE market_id IN (:ids)")
        .param("ids", idList)
        .query(rs -> {
          String description = rs.getString(3);
          if (description != null) {
            rows.get(rs.getObject(1, UUID.class)).descriptions().put(rs.getString(2), description);
          }
        });
    return rows;
  }

  public Optional<UUID> findIdBySlug(String slug) {
    return jdbc.sql("SELECT id FROM market WHERE slug = :slug").param("slug", slug).query(UUID.class).optional();
  }

  public boolean slugExists(String slug) {
    return findIdBySlug(slug).isPresent();
  }

  /** code → (locale → name) for {@code market_type} or {@code cuisine}. */
  public Map<String, Map<String, String>> codeTranslations(String table) {
    String sql =
        switch (table) {
          case "market_type" -> "SELECT market_type_code, locale, name FROM market_type_translation";
          case "cuisine" -> "SELECT cuisine_code, locale, name FROM cuisine_translation";
          default -> throw new IllegalArgumentException(table);
        };
    Map<String, Map<String, String>> result = new HashMap<>();
    jdbc.sql(sql).query(rs -> {
      result.computeIfAbsent(rs.getString(1), k -> new HashMap<>()).put(rs.getString(2), rs.getString(3));
    });
    return result;
  }

  /** (code, icon_key) ordered by sort_order for {@code market_type} or {@code cuisine}. */
  public List<Map.Entry<String, @Nullable String>> codes(String table) {
    String sql =
        switch (table) {
          case "market_type" -> "SELECT code, icon_key FROM market_type ORDER BY sort_order, code";
          case "cuisine" -> "SELECT code, icon_key FROM cuisine ORDER BY sort_order, code";
          default -> throw new IllegalArgumentException(table);
        };
    List<Map.Entry<String, @Nullable String>> codes = new ArrayList<>();
    jdbc.sql(sql).query(rs -> {
      codes.add(new java.util.AbstractMap.SimpleImmutableEntry<>(rs.getString(1), rs.getString(2)));
    });
    return codes;
  }

  public List<BannerRow> findActiveBanners(UUID marketId) {
    Map<UUID, BannerRow> rows = new LinkedHashMap<>();
    jdbc.sql(
            "SELECT id, tint, image_url, target_type, target_id FROM banner WHERE market_id = :marketId AND active"
                + " ORDER BY sort_order, id")
        .param("marketId", marketId)
        .query(rs -> {
          UUID id = rs.getObject(1, UUID.class);
          rows.put(
              id,
              new BannerRow(
                  id,
                  rs.getString(2),
                  rs.getString(3),
                  rs.getString(4),
                  rs.getObject(5, UUID.class),
                  new HashMap<>(),
                  new HashMap<>()));
        });
    if (!rows.isEmpty()) {
      jdbc.sql("SELECT banner_id, locale, overline, title FROM banner_translation WHERE banner_id IN (:ids)")
          .param("ids", List.copyOf(rows.keySet()))
          .query(rs -> {
            BannerRow row = rows.get(rs.getObject(1, UUID.class));
            if (rs.getString(3) != null) {
              row.overlines().put(rs.getString(2), rs.getString(3));
            }
            row.titles().put(rs.getString(2), rs.getString(4));
          });
    }
    return List.copyOf(rows.values());
  }

  public boolean isOwner(UUID marketId, UUID userId) {
    return jdbc.sql("SELECT count(*) FROM market_owner WHERE market_id = :marketId AND user_id = :userId")
            .param("marketId", marketId)
            .param("userId", userId)
            .query(Long.class)
            .single()
        > 0;
  }

  public List<UUID> findOwnedIds(UUID userId) {
    return jdbc.sql(
            "SELECT mo.market_id FROM market_owner mo JOIN market m ON m.id = mo.market_id"
                + " WHERE mo.user_id = :userId ORDER BY m.name, m.id")
        .param("userId", userId)
        .query(UUID.class)
        .list();
  }

  public void addOwner(UUID marketId, UUID userId, Instant now) {
    jdbc.sql(
            "INSERT INTO market_owner (market_id, user_id, created_at) VALUES (:marketId, :userId, :now)"
                + " ON CONFLICT DO NOTHING")
        .param("marketId", marketId)
        .param("userId", userId)
        .param("now", Timestamp.from(now))
        .update();
  }

  public void insert(UUID id, String slug, String status, MarketProfile p, Instant now) {
    jdbc.sql(
            """
            INSERT INTO market (id, slug, name, type_code, status, primary_locale, address_line, lat, lng, phone,
                                logo_url, cover_image_url, tags, delivery, pickup, delivery_radius_km,
                                delivery_fee_cents, free_delivery_from_cents, minimum_order_cents, legal_name,
                                register_info, created_at, updated_at)
            VALUES (:id, :slug, :name, :typeCode, :status, :primaryLocale, :addressLine, :lat, :lng, :phone,
                    :logoUrl, :coverImageUrl, :tags, :delivery, :pickup, :radius, :fee, :freeFrom, :minimum,
                    :legalName, :registerInfo, :now, :now)
            """)
        .paramSource(params(id, slug, status, p, now))
        .update();
    replaceChildren(id, p);
  }

  public void update(UUID id, MarketProfile p, Instant now) {
    jdbc.sql(
            """
            UPDATE market SET name = :name, type_code = :typeCode, primary_locale = :primaryLocale,
                   address_line = :addressLine, lat = :lat, lng = :lng, phone = :phone, logo_url = :logoUrl,
                   cover_image_url = :coverImageUrl, tags = :tags, delivery = :delivery, pickup = :pickup,
                   delivery_radius_km = :radius, delivery_fee_cents = :fee, free_delivery_from_cents = :freeFrom,
                   minimum_order_cents = :minimum, legal_name = :legalName, register_info = :registerInfo,
                   updated_at = :now
             WHERE id = :id
            """)
        .paramSource(params(id, "", "", p, now))
        .update();
    jdbc.sql("DELETE FROM market_cuisine WHERE market_id = :id").param("id", id).update();
    jdbc.sql("DELETE FROM market_opening_hours WHERE market_id = :id").param("id", id).update();
    jdbc.sql("DELETE FROM market_translation WHERE market_id = :id").param("id", id).update();
    replaceChildren(id, p);
  }

  private void replaceChildren(UUID id, MarketProfile p) {
    for (String cuisine : Set.copyOf(p.cuisineCodes())) {
      jdbc.sql("INSERT INTO market_cuisine (market_id, cuisine_code) VALUES (:id, :code)")
          .param("id", id)
          .param("code", cuisine)
          .update();
    }
    for (OpeningHours h : p.openingHours()) {
      jdbc.sql(
              "INSERT INTO market_opening_hours (market_id, day_of_week, opens_at, closes_at)"
                  + " VALUES (:id, :day, :opens, :closes) ON CONFLICT DO NOTHING")
          .param("id", id)
          .param("day", h.dayOfWeek())
          .param("opens", LocalTime.parse(h.opensAt()))
          .param("closes", LocalTime.parse(h.closesAt()))
          .update();
    }
    for (Map.Entry<String, String> description : p.description().entrySet()) {
      jdbc.sql("INSERT INTO market_translation (market_id, locale, description) VALUES (:id, :locale, :description)")
          .param("id", id)
          .param("locale", description.getKey())
          .param("description", description.getValue())
          .update();
    }
  }

  private static Map<String, Object> paramsMap(UUID id, String slug, String status, MarketProfile p, Instant now) {
    Map<String, Object> params = new HashMap<>();
    params.put("id", id);
    params.put("slug", slug);
    params.put("status", status);
    params.put("name", p.name().strip());
    params.put("typeCode", p.typeCode());
    params.put("primaryLocale", p.primaryLocale());
    params.put("addressLine", p.addressLine().strip());
    params.put("lat", p.location().lat());
    params.put("lng", p.location().lng());
    params.put("delivery", p.fulfilment().delivery());
    params.put("pickup", p.fulfilment().pickup());
    params.put("minimum", p.fulfilment().minimumOrderValue().amountCents());
    params.put("legalName", p.legalName().strip());
    params.put("now", Timestamp.from(now));
    return params;
  }

  private static org.springframework.jdbc.core.namedparam.MapSqlParameterSource params(
      UUID id, String slug, String status, MarketProfile p, Instant now) {
    org.springframework.jdbc.core.namedparam.MapSqlParameterSource source =
        new org.springframework.jdbc.core.namedparam.MapSqlParameterSource(paramsMap(id, slug, status, p, now));
    source.addValue("phone", p.phone(), Types.VARCHAR);
    source.addValue("logoUrl", p.logoUrl(), Types.VARCHAR);
    source.addValue("coverImageUrl", p.coverImageUrl(), Types.VARCHAR);
    source.addValue("tags", new SqlTextArray(p.tags()));
    source.addValue("radius", p.fulfilment().deliveryRadiusKm(), Types.DOUBLE);
    source.addValue(
        "fee", p.fulfilment().deliveryFee() == null ? null : p.fulfilment().deliveryFee().amountCents(), Types.BIGINT);
    source.addValue(
        "freeFrom",
        p.fulfilment().freeDeliveryFrom() == null ? null : p.fulfilment().freeDeliveryFrom().amountCents(),
        Types.BIGINT);
    source.addValue("registerInfo", p.registerInfo(), Types.VARCHAR);
    return source;
  }

  private static MarketRow row(ResultSet rs) throws SQLException {
    return new MarketRow(
        rs.getObject("id", UUID.class),
        rs.getString("slug"),
        rs.getString("name"),
        rs.getString("type_code"),
        rs.getString("status"),
        rs.getString("primary_locale"),
        rs.getString("address_line"),
        rs.getDouble("lat"),
        rs.getDouble("lng"),
        rs.getString("phone"),
        rs.getString("logo_url"),
        rs.getString("cover_image_url"),
        SqlTextArray.read(rs.getArray("tags")),
        rs.getBoolean("delivery"),
        rs.getBoolean("pickup"),
        rs.getObject("delivery_radius_km", Double.class),
        rs.getObject("delivery_fee_cents", Long.class),
        rs.getObject("free_delivery_from_cents", Long.class),
        rs.getLong("minimum_order_cents"),
        rs.getString("legal_name"),
        rs.getString("register_info"),
        new ArrayList<>(),
        new ArrayList<>(),
        new HashMap<>());
  }
}
