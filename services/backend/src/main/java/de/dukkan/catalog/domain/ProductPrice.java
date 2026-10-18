package de.dukkan.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** One entry of a product's price history; {@code validTo == null} marks the current price. */
@Entity
@Table(name = "product_price")
public class ProductPrice {

  @Id private UUID id;

  @Column(name = "product_id", nullable = false)
  private UUID productId;

  @Column(name = "amount_cents", nullable = false)
  private long amountCents;

  @Column(nullable = false)
  private String currency;

  @Column(name = "valid_from", nullable = false)
  private Instant validFrom;

  @Column(name = "valid_to")
  private @Nullable Instant validTo;

  protected ProductPrice() {}

  public UUID getProductId() {
    return productId;
  }

  public long getAmountCents() {
    return amountCents;
  }

  public String getCurrency() {
    return currency;
  }

  public Instant getValidFrom() {
    return validFrom;
  }

  public @Nullable Instant getValidTo() {
    return validTo;
  }
}
