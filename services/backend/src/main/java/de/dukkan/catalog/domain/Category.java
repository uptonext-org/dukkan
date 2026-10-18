package de.dukkan.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A product category; categories form a tree via {@code parentId}. */
@Entity
@Table(name = "category")
public class Category {

  @Id private UUID id;

  @Column(name = "parent_id")
  private @Nullable UUID parentId;

  @Column(nullable = false)
  private String slug;

  @Column(nullable = false)
  private String name;

  @Column(name = "icon_key")
  private @Nullable String iconKey;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  protected Category() {}

  public UUID getId() {
    return id;
  }

  public @Nullable UUID getParentId() {
    return parentId;
  }

  public String getSlug() {
    return slug;
  }

  public String getName() {
    return name;
  }

  public @Nullable String getIconKey() {
    return iconKey;
  }

  public int getSortOrder() {
    return sortOrder;
  }
}
