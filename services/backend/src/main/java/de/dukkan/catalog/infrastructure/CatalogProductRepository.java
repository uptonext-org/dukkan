package de.dukkan.catalog.infrastructure;

import de.dukkan.catalog.application.NewProduct;
import de.dukkan.shared.persistence.SqlTextArray;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
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

/** SQL access to catalog products (shared and market-private) and their search columns. */
@Repository
public class CatalogProductRepository {

  /** A catalog product as listed in the merchant catalog search. */
  public record CatalogRow(
      UUID id,
      @Nullable UUID ownerMarketId,
      UUID categoryId,
      @Nullable String brand,
      String unitSize,
      @Nullable String imageUrl,
      boolean organic,
      @Nullable String barcode,
      Map<String, String> names,
      List<String> cuisines,
      @Nullable Boolean alreadyListed) {}

  /** Data needed to (re)build the search columns of a product. */
  public record SearchSource(UUID id, @Nullable String brand, List<String> keywords, Map<String, String> names) {}

  /** A page of catalog rows. */
  public record CatalogPage(List<CatalogRow> rows, long total) {}

  private final JdbcClient jdbc;

  public CatalogProductRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Ownership of an existing catalog product; {@code ownerMarketId} is null for shared products. */
  public record Ownership(@Nullable UUID ownerMarketId) {}

  /** Ownership of a catalog product, empty if it does not exist. */
  public Optional<Ownership> findOwner(UUID id) {
    return jdbc.sql("SELECT owner_market_id FROM catalog_product WHERE id = :id")
        .param("id", id)
        .query((rs, i) -> new Ownership(rs.getObject(1, UUID.class)))
        .optional();
  }

  /** Shared catalog products (owner IS NULL) matching the filters, ordered by localised name. */
  public CatalogPage searchShared(
      @Nullable String term,
      @Nullable String cuisine,
      @Nullable UUID categoryId,
      @Nullable UUID listedInMarket,
      List<String> locales,
      int page,
      int size) {
    StringBuilder where =
        new StringBuilder(
            """
             FROM catalog_product cp
             LEFT JOIN LATERAL (
               SELECT t.name FROM catalog_product_translation t WHERE t.catalog_product_id = cp.id
                ORDER BY CASE t.locale WHEN :l0 THEN 0 WHEN :l1 THEN 1 ELSE 2 END, t.locale LIMIT 1) loc ON true
             WHERE cp.owner_market_id IS NULL
            """);
    if (term != null) {
      where.append(" AND cp.search_text ILIKE :pattern ESCAPE '\\'");
    }
    if (cuisine != null) {
      where.append(" AND EXISTS (SELECT 1 FROM catalog_product_cuisine pc WHERE pc.catalog_product_id = cp.id AND pc.cuisine_code = :cuisine)");
    }
    if (categoryId != null) {
      where.append(
          " AND (cp.category_id = :categoryId OR cp.category_id IN (SELECT c.id FROM category c WHERE c.parent_id = :categoryId))");
    }
    JdbcClient.StatementSpec count = bindSearch(jdbc.sql("SELECT count(*)" + where), term, cuisine, categoryId, locales);
    long total = count.query(Long.class).single();
    if (total == 0) {
      return new CatalogPage(List.of(), 0);
    }
    String listed =
        listedInMarket == null
            ? "NULL"
            : "EXISTS (SELECT 1 FROM market_product mp WHERE mp.catalog_product_id = cp.id AND mp.market_id = :market"
                + " AND mp.deleted_at IS NULL)";
    String sql =
        "SELECT cp.id, cp.owner_market_id, cp.category_id, cp.brand, cp.unit_size, cp.image_url, cp.organic, cp.barcode, "
            + listed
            + " AS listed"
            + where
            + " ORDER BY lower(loc.name), cp.id LIMIT :limit OFFSET :offset";
    JdbcClient.StatementSpec select =
        bindSearch(jdbc.sql(sql), term, cuisine, categoryId, locales).param("limit", size).param("offset", (long) page * size);
    if (listedInMarket != null) {
      select = select.param("market", listedInMarket);
    }
    Map<UUID, CatalogRow> rows = new LinkedHashMap<>();
    select.query(rs -> {
      UUID id = rs.getObject(1, UUID.class);
      rows.put(
          id,
          new CatalogRow(
              id,
              rs.getObject(2, UUID.class),
              rs.getObject(3, UUID.class),
              rs.getString(4),
              rs.getString(5),
              rs.getString(6),
              rs.getBoolean(7),
              rs.getString(8),
              new HashMap<>(),
              new ArrayList<>(),
              rs.getObject(9, Boolean.class)));
    });
    if (!rows.isEmpty()) {
      List<UUID> ids = List.copyOf(rows.keySet());
      jdbc.sql("SELECT catalog_product_id, locale, name FROM catalog_product_translation WHERE catalog_product_id IN (:ids)")
          .param("ids", ids)
          .query(rs -> {
            rows.get(rs.getObject(1, UUID.class)).names().put(rs.getString(2), rs.getString(3));
          });
      jdbc.sql(
              "SELECT catalog_product_id, cuisine_code FROM catalog_product_cuisine WHERE catalog_product_id IN (:ids)"
                  + " ORDER BY cuisine_code")
          .param("ids", ids)
          .query(rs -> {
            rows.get(rs.getObject(1, UUID.class)).cuisines().add(rs.getString(2));
          });
    }
    return new CatalogPage(List.copyOf(rows.values()), total);
  }

  private static JdbcClient.StatementSpec bindSearch(
      JdbcClient.StatementSpec spec,
      @Nullable String term,
      @Nullable String cuisine,
      @Nullable UUID categoryId,
      List<String> locales) {
    JdbcClient.StatementSpec bound =
        spec.param("l0", locales.isEmpty() ? "" : locales.get(0)).param("l1", locales.size() < 2 ? "" : locales.get(1));
    if (term != null) {
      bound = bound.param("pattern", "%" + term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
    }
    if (cuisine != null) {
      bound = bound.param("cuisine", cuisine);
    }
    if (categoryId != null) {
      bound = bound.param("categoryId", categoryId);
    }
    return bound;
  }

  /** Inserts a market-private product with translations and cuisines. */
  public void insertPrivate(UUID id, UUID ownerMarketId, NewProduct p, String searchText, Instant now) {
    jdbc.sql(
            """
            INSERT INTO catalog_product (id, owner_market_id, category_id, brand, unit_size, net_quantity, net_unit,
                                         organic, allergens, keywords, barcode, image_url, origin, search_text,
                                         created_at, updated_at)
            VALUES (:id, :owner, :categoryId, :brand, :unitSize, :netQuantity, :netUnit, :organic, :allergens,
                    :keywords, :barcode, :imageUrl, NULL, :searchText, :now, :now)
            """)
        .param("id", id)
        .param("owner", ownerMarketId)
        .param("categoryId", p.categoryId())
        .param("brand", p.brand(), Types.VARCHAR)
        .param("unitSize", p.unitSize().strip())
        .param("netQuantity", p.netQuantity(), Types.NUMERIC)
        .param("netUnit", p.netUnit(), Types.VARCHAR)
        .param("organic", p.organic())
        .param("allergens", new SqlTextArray(p.allergens()))
        .param("keywords", new SqlTextArray(p.keywords()))
        .param("barcode", p.barcode(), Types.VARCHAR)
        .param("imageUrl", p.imageUrl(), Types.VARCHAR)
        .param("searchText", searchText)
        .param("now", Timestamp.from(now))
        .update();
    for (Map.Entry<String, String> name : p.name().entrySet()) {
      String locale = name.getKey();
      jdbc.sql(
              """
              INSERT INTO catalog_product_translation (catalog_product_id, locale, name, description, tagline,
                                                       ingredients, search_name)
              VALUES (:id, :locale, :name, :description, :tagline, NULL, NULL)
              """)
          .param("id", id)
          .param("locale", locale)
          .param("name", name.getValue().strip())
          .param("description", p.description().get(locale), Types.VARCHAR)
          .param("tagline", p.tagline().get(locale), Types.VARCHAR)
          .update();
    }
    for (String cuisine : Set.copyOf(p.cuisineCodes())) {
      jdbc.sql("INSERT INTO catalog_product_cuisine (catalog_product_id, cuisine_code) VALUES (:id, :code)")
          .param("id", id)
          .param("code", cuisine)
          .update();
    }
  }

  /** Products whose search columns have not been built yet (e.g. loaded by SQL seed data). */
  public List<SearchSource> findWithoutSearchText() {
    Map<UUID, SearchSource> sources = new LinkedHashMap<>();
    jdbc.sql(
            "SELECT cp.id, cp.brand, cp.keywords FROM catalog_product cp WHERE cp.search_text IS NULL"
                + " OR EXISTS (SELECT 1 FROM catalog_product_translation t WHERE t.catalog_product_id = cp.id AND t.search_name IS NULL)")
        .query(rs -> {
          UUID id = rs.getObject(1, UUID.class);
          sources.put(id, new SearchSource(id, rs.getString(2), SqlTextArray.read(rs.getArray(3)), new HashMap<>()));
        });
    if (!sources.isEmpty()) {
      jdbc.sql("SELECT catalog_product_id, locale, name FROM catalog_product_translation WHERE catalog_product_id IN (:ids)")
          .param("ids", List.copyOf(sources.keySet()))
          .query(rs -> {
            sources.get(rs.getObject(1, UUID.class)).names().put(rs.getString(2), rs.getString(3));
          });
    }
    return List.copyOf(sources.values());
  }

  public void updateSearchText(UUID id, String searchText) {
    jdbc.sql("UPDATE catalog_product SET search_text = :text WHERE id = :id").param("id", id).param("text", searchText).update();
  }

  public void updateSearchName(UUID id, String locale, String searchName) {
    jdbc.sql(
            "UPDATE catalog_product_translation SET search_name = :searchName WHERE catalog_product_id = :id AND locale = :locale")
        .param("id", id)
        .param("locale", locale)
        .param("searchName", searchName)
        .update();
  }
}
