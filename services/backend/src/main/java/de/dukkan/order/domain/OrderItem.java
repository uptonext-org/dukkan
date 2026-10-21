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

  public long getLineTotalCents() {
    return lineTotalCents;
  }
}
