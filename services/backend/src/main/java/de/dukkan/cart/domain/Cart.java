package de.dukkan.cart.domain;

import static java.util.UUID.randomUUID;

import de.dukkan.shared.domain.FulfilmentType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/**
 * A user's cart in one market. Holds at most one fulfilment reservation (type, slot, address).
 * Protected by optimistic locking ({@code version}). Timestamps come from the application clock.
 */
@Entity
@Table(name = "cart")
public class Cart implements Persistable<UUID> {

  /** The reservation held by the cart; {@code addressId} is null for pickup. */
  public record Reservation(FulfilmentType type, UUID slotId, @Nullable UUID addressId, Instant expiresAt) {}

  @Id private UUID id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "market_id", nullable = false, updatable = false)
  private UUID marketId;

  @Column(name = "fulfilment_type")
  @Enumerated(EnumType.STRING)
  private @Nullable FulfilmentType fulfilmentType;

  @Column(name = "reserved_slot_id")
  private @Nullable UUID reservedSlotId;

  @Column(name = "reserved_address_id")
  private @Nullable UUID reservedAddressId;

  @Column(name = "reservation_expires_at")
  private @Nullable Instant reservationExpiresAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private long version;

  @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("createdAt ASC, id ASC")
  private List<CartItem> items = new ArrayList<>();

  @Transient private boolean persisted;

  protected Cart() {}

  public Cart(UUID userId, UUID marketId, Instant now) {
    this.id = randomUUID();
    this.userId = userId;
    this.marketId = marketId;
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PostLoad
  @PostPersist
  void markPersisted() {
    persisted = true;
  }

  @Override
  public boolean isNew() {
    return !persisted;
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

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public List<CartItem> getItems() {
    return List.copyOf(items);
  }

  public boolean isEmpty() {
    return items.isEmpty();
  }

  public Optional<CartItem> findItem(UUID listingId) {
    return items.stream().filter(i -> i.getListingId().equals(listingId)).findFirst();
  }

  /** Sets a listing's quantity; 0 removes the line. */
  public void setQuantity(UUID listingId, int quantity, Instant now) {
    if (quantity < 0) {
      throw new IllegalArgumentException("quantity must be >= 0");
    }
    Optional<CartItem> existing = findItem(listingId);
    if (quantity == 0) {
      existing.ifPresent(items::remove);
    } else if (existing.isPresent()) {
      existing.orElseThrow().setQuantity(quantity);
    } else {
      items.add(new CartItem(this, listingId, quantity));
    }
    updatedAt = now;
  }

  public void removeAllItems(Instant now) {
    items.clear();
    updatedAt = now;
  }

  public Optional<Reservation> getReservation() {
    if (fulfilmentType == null || reservedSlotId == null || reservationExpiresAt == null) {
      return Optional.empty();
    }
    return Optional.of(new Reservation(fulfilmentType, reservedSlotId, reservedAddressId, reservationExpiresAt));
  }

  public void reserve(FulfilmentType type, UUID slotId, @Nullable UUID addressId, Instant expiresAt, Instant now) {
    this.fulfilmentType = type;
    this.reservedSlotId = slotId;
    this.reservedAddressId = addressId;
    this.reservationExpiresAt = expiresAt;
    this.updatedAt = now;
  }

  public void clearReservation(Instant now) {
    this.fulfilmentType = null;
    this.reservedSlotId = null;
    this.reservedAddressId = null;
    this.reservationExpiresAt = null;
    this.updatedAt = now;
  }
}
