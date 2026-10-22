package de.dukkan.catalog.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Queries for the admin review of merchant-created catalog products (B-36a). */
@Repository
public class ModerationRepository {

  /** One text of a product in one locale. */
  public record TextRow(String name, @Nullable String description, boolean machineTranslated) {}

  public record ProductRow(
      UUID id,
      @Nullable UUID ownerMarketId,
      @Nullable String marketName,
      UUID categoryId,
      @Nullable String brand,
      String unitSize,
      @Nullable String barcode,
      @Nullable String imageUrl,
      String reviewStatus,
      Instant createdAt,
      Map<String, TextRow> texts) {}

  public record Page(List<ProductRow> rows, long total) {}

  /** A shared product that may be the same as a reviewed one. */
  public record Duplicate(UUID id, String name, String unitSize, @Nullable String barcode) {}

  private static final String SELECT =
      "SELECT cp.id, cp.owner_market_id, m.name AS market_name, cp.category_id, cp.brand, cp.unit_size, cp.barcode,"
          + " cp.image_url, cp.review_status, cp.created_at FROM catalog_product cp LEFT JOIN market m ON m.id = cp.owner_market_id";

  private final JdbcClient jdbc;

  public ModerationRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Products with the status, oldest first (a review queue). */
  public Page findByStatus(String status, int page, int size) {
    long total = jdbc.sql("SELECT count(*) FROM catalog_product WHERE review_status = :status").param("status", status).query(Long.class).single();
    List<ProductRow> rows =
        jdbc.sql(SELECT + " WHERE cp.review_status = :status ORDER BY cp.created_at, cp.id LIMIT :limit OFFSET :offset")
            .param("status", status)
            .param("limit", size)
            .param("offset", (long) page * size)
            .query((rs, i) -> row(rs))
            .list();
    return new Page(withTexts(rows), total);
  }

  public Optional<ProductRow> find(UUID id) {
    List<ProductRow> rows = jdbc.sql(SELECT + " WHERE cp.id = :id").param("id", id).query((rs, i) -> row(rs)).list();
    return withTexts(rows).stream().findFirst();
  }

  /** Shared products with the same barcode or the same German/primary search name and size. */
  public Map<UUID, List<Duplicate>> duplicates(Collection<ProductRow> products) {
    Map<UUID, List<Duplicate>> result = new LinkedHashMap<>();
    for (ProductRow product : products) {
      List<String> searchNames = new ArrayList<>();
      jdbc.sql("SELECT search_name FROM catalog_product_translation WHERE catalog_product_id = :id AND search_name IS NOT NULL")
          .param("id", product.id())
          .query(String.class)
          .list()
          .forEach(searchNames::add);
      String sql =
          """
          SELECT DISTINCT cp.id, t.name, cp.unit_size, cp.barcode FROM catalog_product cp
          JOIN catalog_product_translation t ON t.catalog_product_id = cp.id AND t.locale = 'de'
          WHERE cp.owner_market_id IS NULL AND cp.id <> :id
            AND ((:barcode IS NOT NULL AND cp.barcode = :barcode)
              OR (lower(cp.unit_size) = lower(:unitSize) AND EXISTS (
                    SELECT 1 FROM catalog_product_translation t2
                     WHERE t2.catalog_product_id = cp.id AND t2.search_name IN (:names))))
          ORDER BY t.name LIMIT 5
          """;
      List<Duplicate> found =
          jdbc.sql(sql)
              .param("id", product.id())
              .param("barcode", product.barcode(), java.sql.Types.VARCHAR)
              .param("unitSize", product.unitSize())
              .param("names", searchNames.isEmpty() ? List.of("\u0000") : searchNames)
              .query((rs, i) -> new Duplicate(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4)))
              .list();
      result.put(product.id(), found);
    }
    return result;
  }

  /** Replaces or adds the text of one locale; reviewed text is no longer a machine translation. */
  public void upsertText(UUID id, String locale, String name, @Nullable String description, String searchName) {
    jdbc.sql(
            """
            INSERT INTO catalog_product_translation (catalog_product_id, locale, name, description, search_name, machine_translated)
            VALUES (:id, :locale, :name, :description, :searchName, false)
            ON CONFLICT (catalog_product_id, locale) DO UPDATE
               SET name = EXCLUDED.name, description = EXCLUDED.description, search_name = EXCLUDED.search_name,
                   machine_translated = false
            """)
        .param("id", id)
        .param("locale", locale)
        .param("name", name)
        .param("description", description, java.sql.Types.VARCHAR)
        .param("searchName", searchName)
        .update();
  }

  public void setStatus(UUID id, String status, boolean makeShared, Instant now) {
    jdbc.sql(
            "UPDATE catalog_product SET review_status = :status, reviewed_at = :now, updated_at = :now"
                + (makeShared ? ", owner_market_id = NULL" : "")
                + " WHERE id = :id")
        .param("id", id)
        .param("status", status)
        .param("now", Timestamp.from(now))
        .update();
  }

  private static ProductRow row(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new ProductRow(
        rs.getObject("id", UUID.class),
        rs.getObject("owner_market_id", UUID.class),
        rs.getString("market_name"),
        rs.getObject("category_id", UUID.class),
        rs.getString("brand"),
        rs.getString("unit_size"),
        rs.getString("barcode"),
        rs.getString("image_url"),
        rs.getString("review_status"),
        rs.getTimestamp("created_at").toInstant(),
        new LinkedHashMap<>());
  }

  private List<ProductRow> withTexts(List<ProductRow> rows) {
    if (rows.isEmpty()) {
      return rows;
    }
    Map<UUID, ProductRow> byId = new LinkedHashMap<>();
    rows.forEach(r -> byId.put(r.id(), r));
    jdbc.sql(
            "SELECT catalog_product_id, locale, name, description, machine_translated FROM catalog_product_translation"
                + " WHERE catalog_product_id IN (:ids) ORDER BY locale")
        .param("ids", List.copyOf(byId.keySet()))
        .query(
            rs -> {
              ProductRow row = byId.get(rs.getObject(1, UUID.class));
              if (row != null) {
                row.texts().put(rs.getString(2), new TextRow(rs.getString(3), rs.getString(4), rs.getBoolean(5)));
              }
            });
    return rows;
  }
}
