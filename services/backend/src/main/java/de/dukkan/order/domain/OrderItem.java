package de.dukkan.order.domain;

import static java.util.UUID.randomUUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A line of a placed order with its price and name snapshot. */
@Entity
@Table(name = "order_item")
public class OrderItem {

  @Id private UUID id;

  @JoinColumn(name = "order_id", nullable = false, updatable = false)
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @SuppressWarnings("UnusedVariable") // Owning side of the JPA association; written, read by Hibernate.
  private CustomerOrder order;

  @Column(name = "line_no", nullable = false)
  private int lineNo;

  @Column(name = "market_product_id", nullable = false)
  private UUID listingId;

  @Column(name = "catalog_product_id", nullable = false)
  private UUID catalogProductId;

  @Column(nullable = false)
  private String name;

  @Column(name = "unit_price_cents", nullable = false)
  private long unitPriceCents;

  @Column(nullable = false)
  private int quantity;

  @Column(name = "line_total_cents", nullable = false)
  private long lineTotalCents;

  /** Units the market hands over when fewer than ordered (missing items); null = as ordered. */
  @Column(name = "fulfilled_quantity")
  private @Nullable Integer fulfilledQuantity;

  protected OrderItem() {}

  OrderItem(
      CustomerOrder order,
      int lineNo,
      UUID listingId,
      UUID catalogProductId,
      String name,
      long unitPriceCents,
      int quantity,
      long lineTotalCents) {
    this.id = randomUUID();
    this.order = order;
    this.lineNo = lineNo;
    this.listingId = listingId;
    this.catalogProductId = catalogProductId;
    this.name = name;
    this.unitPriceCents = unitPriceCents;
    this.quantity = quantity;
    this.lineTotalCents = lineTotalCents;
  }

  public int getLineNo() {
    return lineNo;
  }

  public UUID getListingId() {
    return listingId;
  }

  public UUID getCatalogProductId() {
    return catalogProductId;
  }

  public String getName() {
    return name;
  }

  public long getUnitPriceCents() {
    return unitPriceCents;
  }

  public int getQuantity() {
    return quantity;
  }

  /** The line total when ordered. */
  public long getLineTotalCents() {
    return lineTotalCents;
  }

  public @Nullable Integer getFulfilledQuantity() {
    return fulfilledQuantity;
  }

  /** Units charged and handed over. */
  public int getChargedQuantity() {
    return fulfilledQuantity == null ? quantity : fulfilledQuantity;
  }

  /**
   * The amount charged for the line: the ordered total scaled down to the units handed over,
   * rounded down, so a missing item never makes another one dearer (also with multi-buy offers).
   */
  public long getChargedTotalCents() {
    int charged = getChargedQuantity();
    return charged == quantity ? lineTotalCents : Math.floorDiv(lineTotalCents * charged, quantity);
  }

  void setFulfilledQuantity(int units) {
    if (units < 0 || units > quantity) {
      throw new IllegalArgumentException("fulfilled quantity must be between 0 and " + quantity);
    }
    this.fulfilledQuantity = units == quantity ? null : units;
  }
}
