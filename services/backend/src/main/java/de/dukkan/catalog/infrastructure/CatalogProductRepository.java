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
      @Nullable Boolean alreadyListed,
      @Nullable String iconKey) {}

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
            ? "CAST(NULL AS boolean)"
            : "EXISTS (SELECT 1 FROM market_product mp WHERE mp.catalog_product_id = cp.id AND mp.market_id = :market"
                + " AND mp.deleted_at IS NULL)";
    String sql =
        "SELECT cp.id, cp.owner_market_id, cp.category_id, cp.brand, cp.unit_size, cp.image_url, cp.organic, cp.barcode, "
            + listed
            + " AS listed, COALESCE(cp.icon_key, (SELECT c.icon_key FROM category c WHERE c.id = cp.category_id)) AS icon_key"
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
              rs.getObject(9, Boolean.class),
              rs.getString(10)));
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
    insert(id, ownerMarketId, "PENDING", p, searchText, now);
  }

  /** A product of the shared catalog, added by platform admins (no review needed). */
  public void insertShared(UUID id, NewProduct p, String searchText, Instant now) {
    insert(id, null, null, p, searchText, now);
  }

  private void insert(UUID id, @Nullable UUID ownerMarketId, @Nullable String reviewStatus, NewProduct p, String searchText, Instant now) {
    jdbc.sql(
            """
            INSERT INTO catalog_product (id, owner_market_id, category_id, brand, unit_size, net_quantity, net_unit,
                                         organic, allergens, keywords, barcode, image_url, origin, search_text,
                                         review_status, created_at, updated_at)
            VALUES (:id, :owner, :categoryId, :brand, :unitSize, :netQuantity, :netUnit, :organic, :allergens,
                    :keywords, :barcode, :imageUrl, NULL, :searchText, :reviewStatus, :now, :now)
            """)
        .param("id", id)
        .param("owner", ownerMarketId, Types.OTHER)
        .param("reviewStatus", reviewStatus, Types.VARCHAR)
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
    insertTexts(id, p);
  }

  /**
   * Changes the details of a market's own product. The product goes back to the admins' review
   * queue (its texts may now say anything). Texts: the languages sent are written (a machine
   * translation stays marked as one while its text is unchanged; a tagline not sent is kept); a
   * language not sent keeps the merchant's own text, while its machine translation is dropped when a
   * name changed, so the translator writes a new one. False when the product is not that market's.
   */
  public boolean updatePrivate(UUID id, UUID ownerMarketId, NewProduct p, String searchText, Instant now) {
    int updated =
        jdbc.sql(
                """
                UPDATE catalog_product
                   SET category_id = :categoryId, brand = :brand, unit_size = :unitSize, net_quantity = :netQuantity,
                       net_unit = :netUnit, organic = :organic, allergens = :allergens, keywords = :keywords,
                       barcode = :barcode, search_text = :searchText, review_status = 'PENDING', reviewed_at = NULL,
                       updated_at = :now
                 WHERE id = :id AND owner_market_id = :owner
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
            .param("searchText", searchText)
            .param("now", Timestamp.from(now))
            .update();
    if (updated == 0) {
      return false;
    }
    Map<String, String> before = new HashMap<>();
    jdbc.sql("SELECT locale, name FROM catalog_product_translation WHERE catalog_product_id = :id")
        .param("id", id)
        .query(rs -> {
          before.put(rs.getString(1), rs.getString(2));
        });
    boolean renamed = p.name().entrySet().stream().anyMatch(e -> !e.getValue().strip().equals(before.get(e.getKey())));
    for (Map.Entry<String, String> name : p.name().entrySet()) {
      String locale = name.getKey();
      jdbc.sql(
              """
              INSERT INTO catalog_product_translation (catalog_product_id, locale, name, description, tagline,
                                                       ingredients, search_name, machine_translated)
              VALUES (:id, :locale, :name, :description, :tagline, NULL, NULL, false)
              ON CONFLICT (catalog_product_id, locale) DO UPDATE
                 SET machine_translated = catalog_product_translation.machine_translated
                                          AND catalog_product_translation.name = EXCLUDED.name
                                          AND catalog_product_translation.description IS NOT DISTINCT FROM EXCLUDED.description,
                     name = EXCLUDED.name,
                     description = EXCLUDED.description,
                     tagline = COALESCE(EXCLUDED.tagline, catalog_product_translation.tagline)
              """)
          .param("id", id)
          .param("locale", locale)
          .param("name", name.getValue().strip())
          .param("description", p.description().get(locale), Types.VARCHAR)
          .param("tagline", p.tagline().get(locale), Types.VARCHAR)
          .update();
    }
    if (renamed) {
      jdbc.sql("DELETE FROM catalog_product_translation WHERE catalog_product_id = :id AND machine_translated AND NOT (locale = ANY(:sent))")
          .param("id", id)
          .param("sent", new SqlTextArray(List.copyOf(p.name().keySet())))
          .update();
    }
    jdbc.sql("DELETE FROM catalog_product_cuisine WHERE catalog_product_id = :id").param("id", id).update();
    for (String cuisine : Set.copyOf(p.cuisineCodes())) {
      jdbc.sql("INSERT INTO catalog_product_cuisine (catalog_product_id, cuisine_code) VALUES (:id, :code)")
          .param("id", id)
          .param("code", cuisine)
          .update();
    }
    return true;
  }

  /** Every detail of a product in every language, as a merchant edits it. */
  public Optional<NewProduct> details(UUID id) {
    List<NewProduct> found =
        jdbc.sql(
                """
                SELECT category_id, brand, unit_size, net_quantity, net_unit, organic, allergens, keywords, barcode, image_url
                  FROM catalog_product WHERE id = :id
                """)
            .param("id", id)
            .query(
                (rs, i) ->
                    new NewProduct(
                        new LinkedHashMap<>(),
                        new LinkedHashMap<>(),
                        new LinkedHashMap<>(),
                        SqlTextArray.read(rs.getArray(8)),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getBigDecimal(4),
                        rs.getString(5),
                        rs.getObject(1, UUID.class),
                        new ArrayList<>(),
                        rs.getBoolean(6),
                        SqlTextArray.read(rs.getArray(7)),
                        rs.getString(10),
                        rs.getString(9)))
            .list();
    if (found.isEmpty()) {
      return Optional.empty();
    }
    NewProduct product = found.getFirst();
    jdbc.sql("SELECT locale, name, description, tagline FROM catalog_product_translation WHERE catalog_product_id = :id ORDER BY locale")
        .param("id", id)
        .query(
            rs -> {
              String locale = rs.getString(1);
              product.name().put(locale, rs.getString(2));
              if (rs.getString(3) != null) {
                product.description().put(locale, rs.getString(3));
              }
              if (rs.getString(4) != null) {
                product.tagline().put(locale, rs.getString(4));
              }
            });
    product.cuisineCodes()
        .addAll(
            jdbc.sql("SELECT cuisine_code FROM catalog_product_cuisine WHERE catalog_product_id = :id ORDER BY cuisine_code")
                .param("id", id)
                .query(String.class)
                .list());
    return Optional.of(product);
  }

  private void insertTexts(UUID id, NewProduct p) {
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

  public void updateImage(UUID id, @Nullable String imageUrl, Instant now) {
    jdbc.sql("UPDATE catalog_product SET image_url = :imageUrl, updated_at = :now WHERE id = :id")
        .param("id", id)
        .param("imageUrl", imageUrl, Types.VARCHAR)
        .param("now", Timestamp.from(now))
        .update();
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

  /** Texts of one product: locale → name and description, plus brand and keywords for the search text. */
  public record ProductTexts(UUID id, @Nullable UUID ownerMarketId, @Nullable String brand, List<String> keywords, Map<String, String> names, Map<String, String> descriptions) {}

  public Optional<ProductTexts> texts(UUID id) {
    List<ProductTexts> found =
        jdbc.sql("SELECT id, owner_market_id, brand, keywords FROM catalog_product WHERE id = :id")
            .param("id", id)
            .query((rs, i) -> new ProductTexts(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), SqlTextArray.read(rs.getArray(4)), new HashMap<>(), new HashMap<>()))
            .list();
    if (found.isEmpty()) {
      return Optional.empty();
    }
    ProductTexts texts = found.getFirst();
    jdbc.sql("SELECT locale, name, description FROM catalog_product_translation WHERE catalog_product_id = :id")
        .param("id", id)
        .query(rs -> {
          texts.names().put(rs.getString(1), rs.getString(2));
          String description = rs.getString(3);
          if (description != null) {
            texts.descriptions().put(rs.getString(1), description);
          }
        });
    return Optional.of(texts);
  }

  /** Adds a machine translation for a locale the product does not have yet; never overwrites. */
  public boolean insertMachineTranslation(UUID id, String locale, String name, @Nullable String description, String searchName) {
    return jdbc.sql(
                """
                INSERT INTO catalog_product_translation (catalog_product_id, locale, name, description, search_name, machine_translated)
                VALUES (:id, :locale, :name, :description, :searchName, true)
                ON CONFLICT (catalog_product_id, locale) DO NOTHING
                """)
            .param("id", id)
            .param("locale", locale)
            .param("name", name)
            .param("description", description, Types.VARCHAR)
            .param("searchName", searchName)
            .update()
        == 1;
  }

  /** Import matching (B-31): a shared product or one of the market's own with this barcode. */
  public Optional<UUID> findByBarcode(String barcode, UUID marketId) {
    return jdbc.sql(
            """
            SELECT cp.id FROM catalog_product cp
            WHERE cp.barcode = :barcode AND (cp.owner_market_id IS NULL OR cp.owner_market_id = :marketId)
            ORDER BY EXISTS (SELECT 1 FROM market_product mp WHERE mp.catalog_product_id = cp.id AND mp.market_id = :marketId
                             AND mp.deleted_at IS NULL) DESC,
                     cp.owner_market_id IS NULL DESC, cp.id
            LIMIT 1
            """)
        .param("barcode", barcode)
        .param("marketId", marketId)
        .query(UUID.class)
        .optional();
  }

  /**
   * Shared catalog import: a shared product with the same barcode, or with this normalised name in
   * {@code locale} and the same pack size.
   */
  public Optional<UUID> findShared(@Nullable String barcode, String locale, @Nullable String searchName, String unitSize) {
    return jdbc.sql(
            """
            SELECT cp.id FROM catalog_product cp
            WHERE cp.owner_market_id IS NULL
              AND ((CAST(:barcode AS varchar) IS NOT NULL AND cp.barcode = :barcode)
                OR (lower(cp.unit_size) = lower(:unitSize) AND EXISTS (
                      SELECT 1 FROM catalog_product_translation t
                       WHERE t.catalog_product_id = cp.id AND t.locale = :locale AND t.search_name = :searchName)))
            ORDER BY cp.created_at, cp.id
            LIMIT 1
            """)
        .param("barcode", barcode, Types.VARCHAR)
        .param("locale", locale)
        .param("searchName", searchName, Types.VARCHAR)
        .param("unitSize", unitSize)
        .query(UUID.class)
        .optional();
  }

  /** Import matching (B-31): the market's own product with this normalised name and pack size. */
  public Optional<UUID> findPrivateByName(UUID marketId, String locale, String searchName, String unitSize) {
    return jdbc.sql(
            """
            SELECT cp.id FROM catalog_product cp
            JOIN catalog_product_translation t ON t.catalog_product_id = cp.id AND t.locale = :locale
            WHERE cp.owner_market_id = :marketId AND t.search_name = :searchName AND lower(cp.unit_size) = lower(:unitSize)
            ORDER BY cp.created_at, cp.id
            LIMIT 1
            """)
        .param("marketId", marketId)
        .param("locale", locale)
        .param("searchName", searchName)
        .param("unitSize", unitSize)
        .query(UUID.class)
        .optional();
  }
}
