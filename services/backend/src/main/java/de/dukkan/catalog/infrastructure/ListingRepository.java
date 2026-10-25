package de.dukkan.catalog.infrastructure;

import de.dukkan.catalog.application.ListingOverrides;
import de.dukkan.i18n.application.TextNormalizer;
import de.dukkan.shared.persistence.SqlTextArray;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * SQL access to market listings ({@code market_product}) joined with their catalog product,
 * translations and cuisines; plus the language-independent search.
 */
@Repository
public class ListingRepository {

  /** A listing with everything needed to render it in any locale. */
  public record ListingRow(
      UUID id,
      UUID marketId,
      UUID catalogProductId,
      long priceCents,
      boolean available,
      int maxOrderQuantity,
      Instant createdAt,
      boolean deleted,
      @Nullable UUID ownerMarketId,
      UUID categoryId,
      @Nullable String brand,
      String unitSize,
      @Nullable BigDecimal netQuantity,
      @Nullable String netUnit,
      boolean organic,
      List<String> allergens,
      @Nullable String barcode,
      @Nullable String imageUrl,
      @Nullable String origin,
      @Nullable String iconKey,
      ProductTexts texts,
      List<String> cuisines,
      ListingOverrides overrides) {}

  /** Translations of a catalog product, keyed by locale. */
  public record ProductTexts(
      Map<String, String> names,
      Map<String, String> taglines,
      Map<String, String> descriptions,
      Map<String, String> ingredients) {

    static ProductTexts empty() {
      return new ProductTexts(new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
    }
  }

  /** Filters of a single-market search. {@code locales} are the preferred locales for ranking. */
  public record MarketSearch(
      UUID marketId,
      @Nullable String term,
      @Nullable UUID categoryId,
      @Nullable Collection<UUID> onlyListingIds,
      @Nullable Instant createdSince,
      boolean organicOnly,
      boolean availableOnly,
      String sort,
      List<String> locales,
      Instant now) {}

  /** A cross-market search hit with its ranking keys. */
  public record Hit(UUID listingId, UUID marketId, long priceCents, int tier, int nameLength, String name) {}

  /** A page of ids in result order. */
  public record IdPage(List<UUID> ids, long total) {}

  private static final String LISTING_COLUMNS =
      """
      SELECT mp.id, mp.market_id, mp.catalog_product_id, mp.price_cents, mp.available, mp.max_order_quantity,
             mp.created_at, mp.deleted_at IS NOT NULL AS deleted, cp.owner_market_id, cp.category_id,
             COALESCE(mp.brand, cp.brand) AS brand, COALESCE(mp.unit_size, cp.unit_size) AS unit_size,
             mp.name AS own_name, mp.description AS own_description, mp.unit_size AS own_unit_size, mp.brand AS own_brand,
             cp.net_quantity, cp.net_unit, cp.organic, cp.allergens, cp.barcode,
             COALESCE(mp.image_url, cp.image_url) AS image_url, cp.origin,
             COALESCE(cp.icon_key, (SELECT c.icon_key FROM category c WHERE c.id = cp.category_id)) AS icon_key
        FROM market_product mp JOIN catalog_product cp ON cp.id = mp.catalog_product_id
      """;

  /**
   * Picks one translation per catalog product: the first locale of :l0, :l1, :l2, else the
   * alphabetically first one. The market's own name of the listing wins over every translation.
   */
  private static final String LOCALIZED =
      """
       LEFT JOIN LATERAL (
         SELECT COALESCE(mp.name, t.name) AS name, COALESCE(mp.search_name, t.search_name) AS search_name
           FROM catalog_product_translation t
          WHERE t.catalog_product_id = cp.id
          ORDER BY CASE t.locale WHEN :l0 THEN 0 WHEN :l1 THEN 1 WHEN :l2 THEN 2 ELSE 3 END, t.locale
          LIMIT 1) loc ON true
      """;

  /**
   * The unit price a shopper pays now: the newest active promotion wins (as in
   * PromotionService.winningRules); PERCENT_OFF lowers the price with half-up rounding like
   * PricingEngine, MULTI_BUY leaves the unit price alone. Needs {@code :now}.
   */
  private static final String EFFECTIVE_PRICE =
      "(mp.price_cents - COALESCE((SELECT (mp.price_cents * p.percent_off + 50) / 100"
          + " FROM promotion p JOIN promotion_product pp ON pp.promotion_id = p.id"
          + " WHERE pp.market_product_id = mp.id AND p.valid_from <= :now AND p.valid_until > :now"
          + " ORDER BY p.created_at DESC, p.id LIMIT 1), 0))";

  private static final String ANY_NAME_FIRST_WORD =
      "EXISTS (SELECT 1 FROM catalog_product_translation t3 WHERE t3.catalog_product_id = cp.id"
          + " AND (t3.search_name = :word OR t3.search_name LIKE :firstWord ESCAPE '\\'))";

  private static final String ANY_NAME_PREFIX =
      "EXISTS (SELECT 1 FROM catalog_product_translation t2 WHERE t2.catalog_product_id = cp.id"
          + " AND t2.search_name LIKE :prefix ESCAPE '\\')";

  private final JdbcClient jdbc;

  public ListingRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Listings by id, including deleted ones, keyed by id. */
  public Map<UUID, ListingRow> findByIds(Collection<UUID> ids) {
    if (ids.isEmpty()) {
      return Map.of();
    }
    Map<UUID, ListingRow> rows = new LinkedHashMap<>();
    jdbc.sql(LISTING_COLUMNS + " WHERE mp.id IN (:ids)")
        .param("ids", List.copyOf(Set.copyOf(ids)))
        .query(rs -> {
          ListingRow row = row(rs);
          rows.put(row.id(), row);
        });
    attachTexts(rows.values());
    return rows;
  }

  /** Ids of non-deleted listings of the market among {@code ids}. */
  public Set<UUID> findActiveIdsOfMarket(UUID marketId, Collection<UUID> ids) {
    if (ids.isEmpty()) {
      return Set.of();
    }
    return new HashSet<>(
        jdbc.sql("SELECT id FROM market_product WHERE market_id = :marketId AND deleted_at IS NULL AND id IN (:ids)")
            .param("marketId", marketId)
            .param("ids", List.copyOf(Set.copyOf(ids)))
            .query(UUID.class)
            .list());
  }

  /** Search within one market (non-deleted listings), paged in SQL. */
  public IdPage search(MarketSearch search, int page, int size) {
    if (search.onlyListingIds() != null && search.onlyListingIds().isEmpty()) {
      return new IdPage(List.of(), 0);
    }
    StringBuilder where =
        new StringBuilder(" FROM market_product mp JOIN catalog_product cp ON cp.id = mp.catalog_product_id")
            .append(LOCALIZED)
            .append(" WHERE mp.market_id = :marketId AND mp.deleted_at IS NULL");
    appendFilters(where, search);
    long total = bind(jdbc.sql("SELECT count(*)" + where), search).query(Long.class).single();
    if (total == 0) {
      return new IdPage(List.of(), 0);
    }
    String sql = "SELECT mp.id" + where + " ORDER BY " + orderBy(search) + " LIMIT :limit OFFSET :offset";
    List<UUID> ids =
        bind(jdbc.sql(sql), search).param("limit", size).param("offset", (long) page * size).query(UUID.class).list();
    return new IdPage(ids, total);
  }

  /** Localised names of matching listings in one market, best match first. */
  public List<String> suggest(UUID marketId, String term, List<String> locales, int limit) {
    MarketSearch search = new MarketSearch(marketId, term, null, null, null, false, false, "relevance", locales, Instant.EPOCH);
    StringBuilder sql =
        new StringBuilder("SELECT loc.name, min(")
            .append(rankExpression())
            .append(") AS tier FROM market_product mp JOIN catalog_product cp ON cp.id = mp.catalog_product_id")
            .append(LOCALIZED)
            .append(" WHERE mp.market_id = :marketId AND mp.deleted_at IS NULL");
    appendFilters(sql, search);
    sql.append(" GROUP BY loc.name ORDER BY tier, length(loc.name), loc.name LIMIT :limit");
    return bind(jdbc.sql(sql.toString()), search).param("limit", limit).query((rs, i) -> rs.getString(1)).list();
  }

  /** Available listings matching the term in the given markets (capped), with ranking keys. */
  public List<Hit> searchMarkets(Collection<UUID> marketIds, String term, List<String> locales, int cap, Instant now) {
    if (marketIds.isEmpty()) {
      return List.of();
    }
    String sql =
        "SELECT mp.id, mp.market_id, "
            + EFFECTIVE_PRICE
            + ", "
            + rankExpression()
            + " AS tier, length(loc.name) AS len, loc.name"
            + " FROM market_product mp JOIN catalog_product cp ON cp.id = mp.catalog_product_id"
            + LOCALIZED
            + " WHERE mp.market_id IN (:marketIds) AND mp.deleted_at IS NULL AND mp.available"
            + " AND (cp.search_text ILIKE :pattern ESCAPE '\\' OR mp.search_text ILIKE :pattern ESCAPE '\\')"
            + " ORDER BY tier, len, loc.name, mp.id LIMIT :cap";
    MarketSearch search =
        new MarketSearch(new UUID(0, 0), term, null, null, null, false, true, "relevance", locales, now);
    return bind(jdbc.sql(sql), search)
        .param("marketIds", List.copyOf(marketIds))
        .param("cap", cap)
        .param("now", Timestamp.from(now))
        .query(
            (rs, i) ->
                new Hit(
                    rs.getObject(1, UUID.class),
                    rs.getObject(2, UUID.class),
                    rs.getLong(3),
                    rs.getInt(4),
                    rs.getInt(5),
                    rs.getString(6)))
        .list();
  }

  /** Number of non-deleted listings per category in a market. */
  public Map<UUID, Integer> countByCategory(UUID marketId) {
    Map<UUID, Integer> counts = new HashMap<>();
    jdbc.sql(
            "SELECT cp.category_id, count(*) FROM market_product mp JOIN catalog_product cp ON cp.id = mp.catalog_product_id"
                + " WHERE mp.market_id = :marketId AND mp.deleted_at IS NULL GROUP BY cp.category_id")
        .param("marketId", marketId)
        .query(rs -> {
          counts.put(rs.getObject(1, UUID.class), rs.getInt(2));
        });
    return counts;
  }

  /** Merchant view: all non-deleted listings (available or not), optionally filtered by a term. */
  public IdPage merchantListings(UUID marketId, @Nullable String term, List<String> locales, int page, int size) {
    return search(new MarketSearch(marketId, term, null, null, null, false, false, "name", locales, Instant.EPOCH), page, size);
  }

  public Optional<UUID> findActiveListingId(UUID marketId, UUID catalogProductId) {
    return jdbc.sql(
            "SELECT id FROM market_product WHERE market_id = :marketId AND catalog_product_id = :productId"
                + " AND deleted_at IS NULL")
        .param("marketId", marketId)
        .param("productId", catalogProductId)
        .query(UUID.class)
        .optional();
  }

  public void insert(UUID id, UUID marketId, UUID catalogProductId, long priceCents, boolean available, int maxQty, Instant now) {
    jdbc.sql(
            """
            INSERT INTO market_product (id, market_id, catalog_product_id, price_cents, available, max_order_quantity,
                                        created_at, updated_at)
            VALUES (:id, :marketId, :productId, :price, :available, :maxQty, :now, :now)
            """)
        .param("id", id)
        .param("marketId", marketId)
        .param("productId", catalogProductId)
        .param("price", priceCents)
        .param("available", available)
        .param("maxQty", maxQty)
        .param("now", Timestamp.from(now))
        .update();
    insertPrice(id, priceCents, now);
  }

  public void updatePrice(UUID id, long priceCents, Instant now) {
    jdbc.sql("UPDATE market_product_price SET valid_to = :now WHERE market_product_id = :id AND valid_to IS NULL")
        .param("id", id)
        .param("now", Timestamp.from(now))
        .update();
    insertPrice(id, priceCents, now);
    jdbc.sql("UPDATE market_product SET price_cents = :price, updated_at = :now WHERE id = :id")
        .param("id", id)
        .param("price", priceCents)
        .param("now", Timestamp.from(now))
        .update();
  }

  public void updateFlags(UUID id, @Nullable Boolean available, @Nullable Integer maxQty, Instant now) {
    jdbc.sql(
            "UPDATE market_product SET available = coalesce(:available, available),"
                + " max_order_quantity = coalesce(:maxQty, max_order_quantity), updated_at = :now WHERE id = :id")
        .param("id", id)
        .param("available", available, java.sql.Types.BOOLEAN)
        .param("maxQty", maxQty, java.sql.Types.INTEGER)
        .param("now", Timestamp.from(now))
        .update();
  }

  /** The market's own photo of a listing (null falls back to the catalog product image). */
  public void updateImage(UUID id, @Nullable String imageUrl, Instant now) {
    jdbc.sql("UPDATE market_product SET image_url = :imageUrl, updated_at = :now WHERE id = :id")
        .param("id", id)
        .param("imageUrl", imageUrl, java.sql.Types.VARCHAR)
        .param("now", Timestamp.from(now))
        .update();
  }

  public int softDelete(UUID marketId, UUID id, Instant now) {
    return jdbc.sql(
            "UPDATE market_product SET deleted_at = :now, updated_at = :now"
                + " WHERE id = :id AND market_id = :marketId AND deleted_at IS NULL")
        .param("id", id)
        .param("marketId", marketId)
        .param("now", Timestamp.from(now))
        .update();
  }

  private void insertPrice(UUID listingId, long priceCents, Instant now) {
    jdbc.sql(
            "INSERT INTO market_product_price (id, market_product_id, amount_cents, valid_from, valid_to)"
                + " VALUES (:id, :listingId, :price, :now, NULL)")
        .param("id", UUID.randomUUID())
        .param("listingId", listingId)
        .param("price", priceCents)
        .param("now", Timestamp.from(now))
        .update();
  }

  /**
   * Replaces the market's own details of a listing ({@link ListingOverrides#NONE}: catalog values
   * again). {@code searchText} indexes the own name and brand; null without them.
   */
  public void updateOverrides(UUID id, ListingOverrides overrides, @Nullable String searchText, Instant now) {
    jdbc.sql(
            """
            UPDATE market_product SET name = :name, description = :description, unit_size = :unitSize, brand = :brand,
                   search_name = :searchName, search_text = :searchText, updated_at = :now
             WHERE id = :id
            """)
        .param("id", id)
        .param("name", overrides.name(), Types.VARCHAR)
        .param("description", overrides.description(), Types.VARCHAR)
        .param("unitSize", overrides.unitSize(), Types.VARCHAR)
        .param("brand", overrides.brand(), Types.VARCHAR)
        .param("searchName", overrides.name() == null ? null : TextNormalizer.normalize(overrides.name()), Types.VARCHAR)
        .param("searchText", searchText, Types.VARCHAR)
        .param("now", Timestamp.from(now))
        .update();
  }

  // ------------------------------------------------------------------ helpers

  private static void appendFilters(StringBuilder where, MarketSearch search) {
    if (search.term() != null) {
      where.append(" AND (cp.search_text ILIKE :pattern ESCAPE '\\' OR mp.search_text ILIKE :pattern ESCAPE '\\')");
    }
    if (search.categoryId() != null) {
      where.append(
          " AND (cp.category_id = :categoryId OR cp.category_id IN (SELECT c.id FROM category c WHERE c.parent_id = :categoryId))");
    }
    if (search.onlyListingIds() != null) {
      where.append(" AND mp.id IN (:onlyIds)");
    }
    if (search.createdSince() != null) {
      where.append(" AND mp.created_at >= :createdSince");
    }
    if (search.organicOnly()) {
      where.append(" AND cp.organic");
    }
    if (search.availableOnly()) {
      where.append(" AND mp.available");
    }
  }

  /**
   * Relevance tiers, best first: 0 the localised name's first word is the term ("apfel" → "Äpfel
   * Elstar"), 1 any translation's first word is the term, 2 the localised name starts with the term
   * ("Apfelschorle"), 3 any translation starts with it, 4 a later word of the localised name is the
   * term ("Bio Äpfel"), 5 any other match (substring, keyword, brand).
   */
  private static String rankExpression() {
    return "CASE WHEN (loc.search_name = :word OR loc.search_name LIKE :firstWord ESCAPE '\\') THEN 0 WHEN "
        + ANY_NAME_FIRST_WORD
        + " THEN 1 WHEN loc.search_name LIKE :prefix ESCAPE '\\' THEN 2 WHEN "
        + ANY_NAME_PREFIX
        + " THEN 3 WHEN (loc.search_name LIKE :innerWord ESCAPE '\\' OR loc.search_name LIKE :lastWord ESCAPE '\\')"
        + " THEN 4 ELSE 5 END";
  }

  private static String orderBy(MarketSearch search) {
    return switch (search.sort()) {
      case "price_asc" -> EFFECTIVE_PRICE + " ASC, lower(loc.name), mp.id";
      case "price_desc" -> EFFECTIVE_PRICE + " DESC, lower(loc.name), mp.id";
      case "name" -> "lower(loc.name), mp.id";
      case "newest" -> "mp.created_at DESC, mp.id";
      default ->
          search.term() == null
              ? "mp.available DESC, lower(loc.name), mp.id"
              : rankExpression() + ", length(loc.name), lower(loc.name), mp.id";
    };
  }

  private static JdbcClient.StatementSpec bind(JdbcClient.StatementSpec spec, MarketSearch search) {
    List<String> locales = search.locales();
    JdbcClient.StatementSpec bound =
        spec.param("marketId", search.marketId())
            .param("l0", locale(locales, 0))
            .param("l1", locale(locales, 1))
            .param("l2", locale(locales, 2));
    if (search.term() != null) {
      String term = escape(search.term());
      bound =
          bound
              .param("pattern", "%" + term + "%")
              .param("prefix", term + "%")
              .param("word", search.term())
              .param("firstWord", term + " %")
              .param("innerWord", "% " + term + " %")
              .param("lastWord", "% " + term);
    }
    if (search.categoryId() != null) {
      bound = bound.param("categoryId", search.categoryId());
    }
    if (search.sort().startsWith("price_")) {
      bound = bound.param("now", Timestamp.from(search.now()));
    }
    if (search.onlyListingIds() != null) {
      bound = bound.param("onlyIds", List.copyOf(search.onlyListingIds()));
    }
    if (search.createdSince() != null) {
      bound = bound.param("createdSince", Timestamp.from(search.createdSince()));
    }
    return bound;
  }

  private static String locale(List<String> locales, int index) {
    return index < locales.size() ? locales.get(index) : "";
  }

  private static String escape(String term) {
    return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private void attachTexts(Collection<ListingRow> rows) {
    if (rows.isEmpty()) {
      return;
    }
    Map<UUID, List<ListingRow>> byProduct = new HashMap<>();
    rows.forEach(r -> byProduct.computeIfAbsent(r.catalogProductId(), k -> new ArrayList<>()).add(r));
    List<UUID> productIds = List.copyOf(byProduct.keySet());
    jdbc.sql(
            "SELECT catalog_product_id, locale, name, tagline, description, ingredients"
                + " FROM catalog_product_translation WHERE catalog_product_id IN (:ids)")
        .param("ids", productIds)
        .query(rs -> {
          for (ListingRow row : byProduct.get(rs.getObject(1, UUID.class))) {
            String locale = rs.getString(2);
            ProductTexts texts = row.texts();
            texts.names().put(locale, rs.getString(3));
            putIfPresent(texts.taglines(), locale, rs.getString(4));
            putIfPresent(texts.descriptions(), locale, rs.getString(5));
            putIfPresent(texts.ingredients(), locale, rs.getString(6));
          }
        });
    jdbc.sql(
            "SELECT pc.catalog_product_id, pc.cuisine_code FROM catalog_product_cuisine pc"
                + " WHERE pc.catalog_product_id IN (:ids) ORDER BY pc.cuisine_code")
        .param("ids", productIds)
        .query(rs -> {
          for (ListingRow row : byProduct.get(rs.getObject(1, UUID.class))) {
            row.cuisines().add(rs.getString(2));
          }
        });
    rows.forEach(ListingRepository::applyOverrides);
  }

  /** The market's own name and description replace the catalog texts in every language. */
  private static void applyOverrides(ListingRow row) {
    ListingOverrides own = row.overrides();
    Map<String, String> names = row.texts().names();
    if (own.description() != null) {
      String description = own.description();
      Map<String, String> descriptions = row.texts().descriptions();
      descriptions.clear();
      names.keySet().forEach(locale -> descriptions.put(locale, description));
    }
    if (own.name() != null) {
      String name = own.name();
      names.replaceAll((locale, catalogName) -> name);
    }
  }

  private static void putIfPresent(Map<String, String> map, String key, @Nullable String value) {
    if (value != null) {
      map.put(key, value);
    }
  }

  private static ListingRow row(ResultSet rs) throws SQLException {
    return new ListingRow(
        rs.getObject("id", UUID.class),
        rs.getObject("market_id", UUID.class),
        rs.getObject("catalog_product_id", UUID.class),
        rs.getLong("price_cents"),
        rs.getBoolean("available"),
        rs.getInt("max_order_quantity"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getBoolean("deleted"),
        rs.getObject("owner_market_id", UUID.class),
        rs.getObject("category_id", UUID.class),
        rs.getString("brand"),
        rs.getString("unit_size"),
        rs.getBigDecimal("net_quantity"),
        rs.getString("net_unit"),
        rs.getBoolean("organic"),
        SqlTextArray.read(rs.getArray("allergens")),
        rs.getString("barcode"),
        rs.getString("image_url"),
        rs.getString("origin"),
        rs.getString("icon_key"),
        ProductTexts.empty(),
        new ArrayList<>(),
        new ListingOverrides(
            rs.getString("own_name"), rs.getString("own_description"), rs.getString("own_unit_size"), rs.getString("own_brand")));
  }
}
