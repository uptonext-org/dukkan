package de.dukkan.delivery.application;

import java.time.Instant;
import java.util.UUID;

/** Read model of a delivery slot as seen at a point in time. */
public record SlotView(
    UUID id,
    UUID hubId,
    Instant startsAt,
    Instant endsAt,
    Instant orderCutoffAt,
    long feeCents,
    int remainingCapacity,
    boolean available) {}
