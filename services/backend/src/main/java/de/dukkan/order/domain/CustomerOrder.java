package de.dukkan.order.domain;

import static java.util.UUID.randomUUID;

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
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A placed order. Prices, slot and address are snapshots taken at placement time. */
@Entity
@Table(name = "customer_order")
public class CustomerOrder extends AuditedEntity {

  /** Totals of an order in cents. */
  public record Totals(long subtotalCents, long discountTotalCents, long deliveryFeeCents, long totalCents) {}

  @Id private UUID id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "order_number", nullable = false, updatable = false)
  private String orderNumber;

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  private OrderStatus status;

  @Column(name = "placed_at", nullable = false, updatable = false)
  private Instant placedAt;

  @Column(name = "idempotency_key", updatable = false)
  private @Nullable String idempotencyKey;

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

  @Embedded private AddressSnapshot address;

  @Version private long version;

  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("lineNo ASC")
  private List<OrderItem> items = new ArrayList<>();

  protected CustomerOrder() {}

  public CustomerOrder(
      UUID userId,
      String orderNumber,
      Instant placedAt,
      @Nullable String idempotencyKey,
      Totals totals,
      SlotSnapshot slot,
      AddressSnapshot address) {
    this.id = randomUUID();
    this.userId = userId;
    this.orderNumber = orderNumber;
    this.status = OrderStatus.PLACED;
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

  public void addItem(UUID productId, String name, long unitPriceCents, int quantity, long lineTotalCents) {
    items.add(new OrderItem(this, items.size() + 1, productId, name, unitPriceCents, quantity, lineTotalCents));
  }

  /** Cancellation is allowed only while PLACED and before the slot's order cutoff. */
  public boolean isCancellable(Instant now) {
    return status == OrderStatus.PLACED && now.isBefore(slot.orderCutoffAt());
  }

  public void cancel(Instant now) {
    if (!isCancellable(now)) {
      throw new IllegalStateException("Order " + orderNumber + " cannot be cancelled");
    }
    status = OrderStatus.CANCELLED;
  }

  @Override
  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getOrderNumber() {
    return orderNumber;
  }

  public OrderStatus getStatus() {
    return status;
  }

  public Instant getPlacedAt() {
    return placedAt;
  }

  public Totals getTotals() {
    return new Totals(subtotalCents, discountTotalCents, deliveryFeeCents, totalCents);
  }

  public SlotSnapshot getSlot() {
    return slot;
  }

  public AddressSnapshot getAddress() {
    return address;
  }

  public List<OrderItem> getItems() {
    return List.copyOf(items);
  }
}
