package de.dukkan.order.domain;

import static java.util.UUID.randomUUID;

import de.dukkan.shared.domain.FulfilmentType;
import de.dukkan.shared.persistence.AuditedEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A placed order in one market. Names, prices, slot and address are snapshots taken at placement. */
@Entity
@Table(name = "customer_order")
public class CustomerOrder extends AuditedEntity {

  /** Totals of an order in cents. */
  public record Totals(long subtotalCents, long discountTotalCents, long deliveryFeeCents, long totalCents) {}

  /** Who and where the order is for. */
  public record Parties(UUID userId, String customerName, UUID marketId, String marketName) {}

  @Id private UUID id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "market_id", nullable = false, updatable = false)
  private UUID marketId;

  @Column(name = "order_number", nullable = false, updatable = false)
  private String orderNumber;

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  private OrderStatus status;

  @Column(name = "fulfilment_type", nullable = false, updatable = false)
  @Enumerated(EnumType.STRING)
  private FulfilmentType fulfilmentType;

  @Column(name = "placed_at", nullable = false, updatable = false)
  private Instant placedAt;

  @Column(name = "idempotency_key", updatable = false)
  private @Nullable String idempotencyKey;

  @Column(name = "market_name", nullable = false, updatable = false)
  private String marketName;

  @Column(name = "customer_name", nullable = false, updatable = false)
  private String customerName;

  @Column(name = "subtotal_cents", nullable = false)
  private long subtotalCents;

  @Column(name = "discount_total_cents", nullable = false)
  private long discountTotalCents;

  @Column(name = "delivery_fee_cents", nullable = false)
  private long deliveryFeeCents;

  @Column(name = "total_cents", nullable = false)
  private long totalCents;

  @Column(nullable = false)
  private String currency;

  @Embedded private SlotSnapshot slot;

  @Embedded private @Nullable AddressSnapshot address;

  /** The total when ordered, kept once items were adjusted (missing items); null otherwise. */
  @Column(name = "original_total_cents")
  private @Nullable Long originalTotalCents;

  @Column(name = "adjusted_at")
  private @Nullable Instant adjustedAt;

  @Version private long version;

  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("lineNo ASC")
  private List<OrderItem> items = new ArrayList<>();

  protected CustomerOrder() {}

  public CustomerOrder(
      Parties parties,
      String orderNumber,
      FulfilmentType fulfilmentType,
      Instant placedAt,
      @Nullable String idempotencyKey,
      Totals totals,
      SlotSnapshot slot,
      @Nullable AddressSnapshot address) {
    this.id = randomUUID();
    this.userId = parties.userId();
    this.customerName = parties.customerName();
    this.marketId = parties.marketId();
    this.marketName = parties.marketName();
    this.orderNumber = orderNumber;
    this.status = OrderStatus.PLACED;
    this.fulfilmentType = fulfilmentType;
    this.placedAt = placedAt;
    this.idempotencyKey = idempotencyKey;
    this.subtotalCents = totals.subtotalCents();
    this.discountTotalCents = totals.discountTotalCents();
    this.deliveryFeeCents = totals.deliveryFeeCents();
    this.totalCents = totals.totalCents();
    this.currency = "EUR";
    this.slot = slot;
    this.address = address;
  }

  public void addItem(UUID listingId, UUID catalogProductId, String name, long unitPriceCents, int quantity, long lineTotalCents) {
    items.add(
        new OrderItem(this, items.size() + 1, listingId, catalogProductId, name, unitPriceCents, quantity, lineTotalCents));
  }

  /** Customers may cancel PLACED or ACCEPTED orders before the slot's order cutoff. */
  public boolean isCancellable(Instant now) {
    return status.isCancellableByCustomer() && now.isBefore(slot.orderCutoffAt());
  }

  public void cancel(Instant now) {
    if (!isCancellable(now)) {
      throw new IllegalStateException("Order " + orderNumber + " cannot be cancelled");
    }
    status = OrderStatus.CANCELLED;
  }

  /** Items can be adjusted while the market packs: before it hands the order over. */
  public boolean isAdjustable() {
    return status == OrderStatus.PLACED || status == OrderStatus.ACCEPTED;
  }

  /**
   * Sets the units handed over per line number (missing items) and recomputes the totals from the
   * lines; the delivery fee stays. Returns the number of lines whose quantity changed.
   *
   * @throws IllegalStateException when the order is no longer adjustable or nothing would be left
   * @throws IllegalArgumentException for unknown lines or quantities outside 0..ordered
   */
  public int adjustItems(Map<Integer, Integer> unitsByLine, Instant now) {
    if (!isAdjustable()) {
      throw new IllegalStateException("Order " + orderNumber + " can no longer be adjusted");
    }
    for (Integer lineNo : unitsByLine.keySet()) {
      if (items.stream().noneMatch(i -> i.getLineNo() == lineNo)) {
        throw new IllegalArgumentException("unknown line " + lineNo);
      }
    }
    int remaining = 0;
    for (OrderItem item : items) {
      Integer units = unitsByLine.get(item.getLineNo());
      remaining += units == null ? item.getChargedQuantity() : units;
    }
    if (remaining == 0) {
      throw new IllegalStateException("Nothing of order " + orderNumber + " would be left; reject it instead");
    }
    long before = totalCents;
    int changed = 0;
    for (OrderItem item : items) {
      Integer units = unitsByLine.get(item.getLineNo());
      if (units != null && units != item.getChargedQuantity()) {
        item.setFulfilledQuantity(units);
        changed++;
      }
    }
    if (changed == 0) {
      return 0;
    }
    long subtotal = 0;
    long merchandise = 0;
    boolean anyShort = false;
    for (OrderItem item : items) {
      subtotal += item.getUnitPriceCents() * item.getChargedQuantity();
      merchandise += item.getChargedTotalCents();
      anyShort |= item.getFulfilledQuantity() != null;
    }
    if (originalTotalCents == null) {
      originalTotalCents = before;
    }
    subtotalCents = subtotal;
    discountTotalCents = subtotal - merchandise;
    totalCents = merchandise + deliveryFeeCents;
    if (!anyShort) {
      // Everything handed over again as ordered.
      originalTotalCents = null;
    }
    adjustedAt = now;
    return changed;
  }

  public @Nullable Long getOriginalTotalCents() {
    return originalTotalCents;
  }

  public @Nullable Instant getAdjustedAt() {
    return adjustedAt;
  }

  /** Merchant transition; returns false (and changes nothing) if it is not allowed. */
  public boolean advanceTo(OrderStatus target) {
    if (!status.canAdvanceTo(target, fulfilmentType)) {
      return false;
    }
    status = target;
    return true;
  }

  @Override
  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getMarketId() {
    return marketId;
  }

  public String getOrderNumber() {
    return orderNumber;
  }

  public OrderStatus getStatus() {
    return status;
  }

  public FulfilmentType getFulfilmentType() {
    return fulfilmentType;
  }

  public Instant getPlacedAt() {
    return placedAt;
  }

  public String getMarketName() {
    return marketName;
  }

  public String getCustomerName() {
    return customerName;
  }

  public Totals getTotals() {
    return new Totals(subtotalCents, discountTotalCents, deliveryFeeCents, totalCents);
  }

  public SlotSnapshot getSlot() {
    return slot;
  }

  public @Nullable AddressSnapshot getAddress() {
    return address;
  }

  public List<OrderItem> getItems() {
    return List.copyOf(items);
  }
}
