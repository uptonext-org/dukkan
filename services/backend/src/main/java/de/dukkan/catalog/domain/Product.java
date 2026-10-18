package de.dukkan.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/** A sellable product. Prices live in {@link ProductPrice} (history with an open-ended current row). */
@Entity
@Table(name = "product")
public class Product {

  @Id private UUID id;

  @Column(name = "category_id", nullable = false)
  private UUID categoryId;

  @Column(nullable = false)
  private String sku;

  @Column(nullable = false)
  private String name;

  private @Nullable String brand;

  @Column(name = "unit_size", nullable = false)
  private String unitSize;

  /** Net content in {@link #netUnit} (e.g. 0.5 for 500 g), used for the comparison price. */
  @Column(name = "net_quantity")
  private @Nullable BigDecimal netQuantity;

  @Column(name = "net_unit")
  private @Nullable String netUnit;

  @Column(name = "image_url")
  private @Nullable String imageUrl;

  private @Nullable String description;

  private @Nullable String ingredients;

  @Column(nullable = false)
  @JdbcTypeCode(SqlTypes.ARRAY)
  private List<String> allergens = List.of();

  @Column(nullable = false)
  @JdbcTypeCode(SqlTypes.ARRAY)
  private List<String> tags = List.of();

  private @Nullable String origin;

  @Column(name = "max_order_quantity", nullable = false)
  private int maxOrderQuantity;

  @Column(nullable = false)
  private boolean available;

  protected Product() {}

  public UUID getId() {
    return id;
  }

  public UUID getCategoryId() {
    return categoryId;
  }

  public String getSku() {
    return sku;
  }

  public String getName() {
    return name;
  }

  public @Nullable String getBrand() {
    return brand;
  }

  public String getUnitSize() {
    return unitSize;
  }

  public @Nullable BigDecimal getNetQuantity() {
    return netQuantity;
  }

  public @Nullable String getNetUnit() {
    return netUnit;
  }

  public @Nullable String getImageUrl() {
    return imageUrl;
  }

  public @Nullable String getDescription() {
    return description;
  }

  public @Nullable String getIngredients() {
    return ingredients;
  }

  public List<String> getAllergens() {
    return List.copyOf(allergens);
  }

  public List<String> getTags() {
    return List.copyOf(tags);
  }

  public @Nullable String getOrigin() {
    return origin;
  }

  public int getMaxOrderQuantity() {
    return maxOrderQuantity;
  }

  public boolean isAvailable() {
    return available;
  }
}
