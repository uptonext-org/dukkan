package de.dukkan.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;
import java.util.UUID;

/** Copy of the delivery slot taken when the order was placed. */
@Embeddable
public record SlotSnapshot(
    @Column(name = "slot_id", nullable = false) UUID slotId,
    @Column(name = "slot_starts_at", nullable = false) Instant startsAt,
    @Column(name = "slot_ends_at", nullable = false) Instant endsAt,
    @Column(name = "slot_order_cutoff_at", nullable = false) Instant orderCutoffAt) {}
