package de.dukkan.catalog.infrastructure;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CategoryRepository {

  /** A category with all name translations. */
  public record CategoryRow(
      UUID id,
      @Nullable UUID parentId,
      String slug,
      @Nullable String iconKey,
      @Nullable String imageUrl,
      int sortOrder,
      Map<String, String> names) {}

  private final JdbcClient jdbc;

  public CategoryRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** All categories ordered by sort order. */
  public List<CategoryRow> findAll() {
    Map<UUID, CategoryRow> rows = new LinkedHashMap<>();
    jdbc.sql("SELECT id, parent_id, slug, icon_key, image_url, sort_order FROM category ORDER BY sort_order, slug")
        .query(rs -> {
          UUID id = rs.getObject(1, UUID.class);
          rows.put(
              id,
              new CategoryRow(
                  id,
                  rs.getObject(2, UUID.class),
                  rs.getString(3),
                  rs.getString(4),
                  rs.getString(5),
                  rs.getInt(6),
                  new HashMap<>()));
        });
    jdbc.sql("SELECT category_id, locale, name FROM category_translation")
        .query(rs -> {
          CategoryRow row = rows.get(rs.getObject(1, UUID.class));
          if (row != null) {
            row.names().put(rs.getString(2), rs.getString(3));
          }
        });
    return List.copyOf(rows.values());
  }
}
