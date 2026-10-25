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

  /** Charged amount when fewer units are handed over; null = {@link #lineTotalCents}. */
  @Column(name = "charged_total_cents")
  private @Nullable Long chargedTotalCents;

  @Column(name = "promo_percent_off")
  private @Nullable Integer promoPercentOff;

  @Column(name = "promo_bundle_quantity")
  private @Nullable Integer promoBundleQuantity;

  @Column(name = "promo_bundle_price_cents")
  private @Nullable Long promoBundlePriceCents;

  @Column(name = "promo_offer_price_cents")
  private @Nullable Long promoOfferPriceCents;

  /** All quantity prices of a multi-buy, e.g. {@code 3:500;7:1000}. */
  @Column(name = "promo_tiers")
  private @Nullable String promoTiers;

  /**
   * The promotion that applied when ordering, kept to re-price fewer units: a percentage, quantity
   * prices ({@code tiers} as {@code 3:500;7:1000}, the smallest also in {@code bundle*}) or an offer
   * price. Lines ordered before quantity tiers existed only have {@code bundle*}.
   */
  public record AppliedPromotion(
      @Nullable Integer percentOff,
      @Nullable Integer bundleQuantity,
      @Nullable Long bundlePriceCents,
      @Nullable Long offerPriceCents,
      @Nullable String tiers) {
    public static final AppliedPromotion NONE = new AppliedPromotion(null, null, null, null, null);
  }

  protected OrderItem() {}

  OrderItem(
      CustomerOrder order,
      int lineNo,
      UUID listingId,
      UUID catalogProductId,
      String name,
      long unitPriceCents,
      int quantity,
      long lineTotalCents,
      AppliedPromotion promotion) {
    this.id = randomUUID();
    this.order = order;
    this.lineNo = lineNo;
    this.listingId = listingId;
    this.catalogProductId = catalogProductId;
    this.name = name;
    this.unitPriceCents = unitPriceCents;
    this.quantity = quantity;
    this.lineTotalCents = lineTotalCents;
    this.promoPercentOff = promotion.percentOff();
    this.promoBundleQuantity = promotion.bundleQuantity();
    this.promoBundlePriceCents = promotion.bundlePriceCents();
    this.promoOfferPriceCents = promotion.offerPriceCents();
    this.promoTiers = promotion.tiers();
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

  /** The amount charged for the line (lower when fewer units are handed over). */
  public long getChargedTotalCents() {
    return chargedTotalCents == null ? lineTotalCents : chargedTotalCents;
  }

  public AppliedPromotion getPromotion() {
    return new AppliedPromotion(promoPercentOff, promoBundleQuantity, promoBundlePriceCents, promoOfferPriceCents, promoTiers);
  }

  /** Units handed over and their price (computed by the application, see {@code ChargedPrice}). */
  void setFulfilled(int units, long chargedCents) {
    if (units < 0 || units > quantity) {
      throw new IllegalArgumentException("fulfilled quantity must be between 0 and " + quantity);
    }
    if (units == quantity) {
      this.fulfilledQuantity = null;
      this.chargedTotalCents = null;
    } else {
      this.fulfilledQuantity = units;
      this.chargedTotalCents = Math.clamp(chargedCents, 0, lineTotalCents);
    }
  }
}
