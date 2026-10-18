package de.dukkan.catalog.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Product search in SQL: case-insensitive {@code LIKE} over {@code lower(name)} (indexed), brand
 * and tags, combinable with category (including sub-categories) and promotion filters.
 */
@Repository
public class ProductSearchRepository {

  /** Sort orders supported by the search. */
  public enum Sort {
    RELEVANCE,
    PRICE_ASC,
    PRICE_DESC,
    NAME
  }

  /** Search criteria; {@code promotedIds} restricts results to these products when non-null. */
  public record Criteria(
      @Nullable UUID categoryId, @Nullable String query, @Nullable Collection<UUID> promotedIds, Sort sort) {}

  /** A page of matching product ids, in result order. */
  public record IdPage(List<UUID> ids, long total) {}

  private static final String FROM =
      """
       FROM product p
       JOIN product_price pp ON pp.product_id = p.id AND pp.valid_to IS NULL
      WHERE 1 = 1
      """;

  private final JdbcClient jdbc;

  public ProductSearchRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public IdPage search(Criteria criteria, int page, int size) {
    if (criteria.promotedIds() != null && criteria.promotedIds().isEmpty()) {
      return new IdPage(List.of(), 0);
    }
    StringBuilder where = new StringBuilder(FROM);
    if (criteria.categoryId() != null) {
      where.append(
          " AND (p.category_id = :categoryId OR p.category_id IN (SELECT c.id FROM category c WHERE c.parent_id = :categoryId))");
    }
    String query = criteria.query();
    if (query != null) {
      where.append(
          " AND (lower(p.name) LIKE :pattern ESCAPE '\\' OR lower(coalesce(p.brand, '')) LIKE :pattern ESCAPE '\\'"
              + " OR lower(array_to_string(p.tags, ' ')) LIKE :pattern ESCAPE '\\')");
    }
    if (criteria.promotedIds() != null) {
      where.append(" AND p.id IN (:promotedIds)");
    }

    long total = bind(jdbc.sql("SELECT count(*)" + where), criteria).query(Long.class).single();
    if (total == 0) {
      return new IdPage(List.of(), 0);
    }
    String sql = "SELECT p.id" + where + " ORDER BY " + orderBy(criteria) + " LIMIT :limit OFFSET :offset";
    List<UUID> ids =
        bind(jdbc.sql(sql), criteria)
            .param("limit", size)
            .param("offset", (long) page * size)
            .query(UUID.class)
            .list();
    return new IdPage(ids, total);
  }

  /** Product names matching {@code query}, prefix matches first. */
  public List<String> suggestNames(String query, int limit) {
    return jdbc.sql(
            """
            SELECT p.name FROM product p
             WHERE lower(p.name) LIKE :pattern ESCAPE '\\'
                OR lower(coalesce(p.brand, '')) LIKE :pattern ESCAPE '\\'
                OR lower(array_to_string(p.tags, ' ')) LIKE :pattern ESCAPE '\\'
             ORDER BY CASE WHEN lower(p.name) LIKE :prefix ESCAPE '\\' THEN 0
                           WHEN lower(p.name) LIKE :pattern ESCAPE '\\' THEN 1 ELSE 2 END,
                      p.available DESC, length(p.name), p.name
             LIMIT :limit
            """)
        .param("pattern", contains(query))
        .param("prefix", prefix(query))
        .param("limit", limit)
        .query(String.class)
        .list();
  }

  private static String orderBy(Criteria criteria) {
    return switch (criteria.sort()) {
      case RELEVANCE ->
          criteria.query() == null
              ? "lower(p.name), p.id"
              : "CASE WHEN lower(p.name) LIKE :prefix ESCAPE '\\' THEN 0"
                  + " WHEN lower(p.name) LIKE :pattern ESCAPE '\\' THEN 1 ELSE 2 END, lower(p.name), p.id";
      case PRICE_ASC -> "pp.amount_cents ASC, lower(p.name), p.id";
      case PRICE_DESC -> "pp.amount_cents DESC, lower(p.name), p.id";
      case NAME -> "lower(p.name), p.id";
    };
  }

  private static JdbcClient.StatementSpec bind(JdbcClient.StatementSpec spec, Criteria criteria) {
    JdbcClient.StatementSpec bound = spec;
    if (criteria.categoryId() != null) {
      bound = bound.param("categoryId", criteria.categoryId());
    }
    String query = criteria.query();
    if (query != null) {
      bound = bound.param("pattern", contains(query)).param("prefix", prefix(query));
    }
    if (criteria.promotedIds() != null) {
      bound = bound.param("promotedIds", List.copyOf(criteria.promotedIds()));
    }
    return bound;
  }

  private static String contains(String query) {
    return "%" + escape(query) + "%";
  }

  private static String prefix(String query) {
    return escape(query) + "%";
  }

  private static String escape(String query) {
    return query
        .strip()
        .toLowerCase(Locale.ROOT)
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_");
  }
}
