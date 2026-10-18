package de.dukkan.cart.domain;

import de.dukkan.shared.persistence.AuditedEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A user's single active cart. Holds at most one delivery slot reservation. Protected by
 * optimistic locking ({@code version}).
 */
@Entity
@Table(name = "cart")
public class Cart extends AuditedEntity {

  /** The reservation currently held by the cart. */
  public record Reservation(UUID slotId, UUID addressId, Instant expiresAt) {}

  @Id private UUID id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "reserved_slot_id")
  private @Nullable UUID reservedSlotId;

  @Column(name = "reserved_address_id")
  private @Nullable UUID reservedAddressId;

  @Column(name = "reservation_expires_at")
  private @Nullable Instant reservationExpiresAt;

  @Version private long version;

  @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("createdAt ASC, id ASC")
  private List<CartItem> items = new ArrayList<>();

  protected Cart() {}

  @Override
  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public List<CartItem> getItems() {
    return List.copyOf(items);
  }

  public Optional<CartItem> findItem(UUID productId) {
    return items.stream().filter(i -> i.getProductId().equals(productId)).findFirst();
  }

  /** Sets a product's quantity; 0 removes the line. */
  public void setQuantity(UUID productId, int quantity) {
    if (quantity < 0) {
      throw new IllegalArgumentException("quantity must be >= 0");
    }
    Optional<CartItem> existing = findItem(productId);
    if (quantity == 0) {
      existing.ifPresent(items::remove);
    } else if (existing.isPresent()) {
      existing.orElseThrow().setQuantity(quantity);
    } else {
      items.add(new CartItem(this, productId, quantity));
    }
  }

  public void removeAllItems() {
    items.clear();
  }

  public Optional<Reservation> getReservation() {
    if (reservedSlotId == null || reservedAddressId == null || reservationExpiresAt == null) {
      return Optional.empty();
    }
    return Optional.of(new Reservation(reservedSlotId, reservedAddressId, reservationExpiresAt));
  }

  /** The slot whose capacity this cart holds, even if the reserved address was deleted since. */
  public Optional<UUID> getReservedSlotId() {
    return Optional.ofNullable(reservedSlotId);
  }

  public void reserve(UUID slotId, UUID addressId, Instant expiresAt) {
    this.reservedSlotId = slotId;
    this.reservedAddressId = addressId;
    this.reservationExpiresAt = expiresAt;
  }

  public void clearReservation() {
    this.reservedSlotId = null;
    this.reservedAddressId = null;
    this.reservationExpiresAt = null;
  }
}
