package de.dukkan.fulfilment.domain;

import de.dukkan.shared.domain.FulfilmentType;
import de.dukkan.shared.persistence.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * A one-hour delivery or pickup window of a market. {@code usedCapacity} counts active cart
 * reservations and open orders; it never exceeds {@code capacity} (row lock + CHECK constraint).
 */
@Entity
@Table(name = "fulfilment_slot")
public class FulfilmentSlot extends AuditedEntity {

  @Id private UUID id;

  @Column(name = "market_id", nullable = false, updatable = false)
  private UUID marketId;

  @Column(nullable = false, updatable = false)
  @Enumerated(EnumType.STRING)
  private FulfilmentType type;

  @Column(name = "starts_at", nullable = false, updatable = false)
  private Instant startsAt;

  @Column(name = "ends_at", nullable = false, updatable = false)
  private Instant endsAt;

  @Column(name = "order_cutoff_at", nullable = false, updatable = false)
  private Instant orderCutoffAt;

  @Column(nullable = false)
  private int capacity;

  @Column(name = "used_capacity", nullable = false)
  private int usedCapacity;

  @Version private long version;

  protected FulfilmentSlot() {}

  public boolean isCutoffPassed(Instant now) {
    return !now.isBefore(orderCutoffAt);
  }

  public int remainingCapacity() {
    return Math.max(0, capacity - usedCapacity);
  }

  /** Takes one unit of capacity. Callers must hold a row lock and check {@link #remainingCapacity}. */
  public void consumeOne() {
    if (remainingCapacity() <= 0) {
      throw new IllegalStateException("Slot " + id + " is full");
    }
    usedCapacity++;
  }

  /** Returns one unit of capacity (never below zero). */
  public void releaseOne() {
    usedCapacity = Math.max(0, usedCapacity - 1);
  }

  @Override
  public UUID getId() {
    return id;
  }

  public UUID getMarketId() {
    return marketId;
  }

  public FulfilmentType getType() {
    return type;
  }

  public Instant getStartsAt() {
    return startsAt;
  }

  public Instant getEndsAt() {
    return endsAt;
  }

  public Instant getOrderCutoffAt() {
    return orderCutoffAt;
  }

  public int getCapacity() {
    return capacity;
  }

  public int getUsedCapacity() {
    return usedCapacity;
  }
}
