package de.dukkan.fulfilment.application;

import de.dukkan.shared.domain.FulfilmentType;
import java.time.Instant;
import java.util.UUID;

/** A slot as seen at a point in time; {@code feeCents} is the market's fee for the slot type. */
public record SlotView(
    UUID id,
    UUID marketId,
    FulfilmentType type,
    Instant startsAt,
    Instant endsAt,
    Instant orderCutoffAt,
    long feeCents,
    int remainingCapacity,
    boolean available) {}
