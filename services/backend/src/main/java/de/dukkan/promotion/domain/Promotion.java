package de.dukkan.promotion.domain;

import de.dukkan.shared.persistence.AuditedEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A time-boxed promotion on a set of products (seed data, read-only in the MVP). */
@Entity
@Table(name = "promotion")
public class Promotion extends AuditedEntity {

  @Id private UUID id;

  @Column(nullable = false)
  private String title;

  private @Nullable String description;

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  private PromotionType type;

  @Column(name = "percent_off")
  private @Nullable Integer percentOff;

  @Column(name = "bundle_quantity")
  private @Nullable Integer bundleQuantity;

  @Column(name = "bundle_price_cents")
  private @Nullable Long bundlePriceCents;

  @Column(name = "valid_from", nullable = false)
  private Instant validFrom;

  @Column(name = "valid_until", nullable = false)
  private Instant validUntil;

  @CollectionTable(name = "promotion_product", joinColumns = @JoinColumn(name = "promotion_id"))
  @Column(name = "product_id", nullable = false)
  @ElementCollection(fetch = FetchType.EAGER)
  private Set<UUID> productIds = new HashSet<>();

  protected Promotion() {}

  @Override
  public UUID getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public @Nullable String getDescription() {
    return description;
  }

  public PromotionType getType() {
    return type;
  }

  public @Nullable Integer getPercentOff() {
    return percentOff;
  }

  public @Nullable Integer getBundleQuantity() {
    return bundleQuantity;
  }

  public @Nullable Long getBundlePriceCents() {
    return bundlePriceCents;
  }

  public Instant getValidFrom() {
    return validFrom;
  }

  public Instant getValidUntil() {
    return validUntil;
  }

  public Set<UUID> getProductIds() {
    return Set.copyOf(productIds);
  }
}
