package de.dukkan.fulfilment.api;

import de.dukkan.fulfilment.application.SlotView;
import de.dukkan.shared.api.Money;
import de.dukkan.shared.time.DukkanTime;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Contract schema {@code Slot}; reused by the cart and order APIs. */
public record SlotResponse(
    UUID id,
    String type,
    OffsetDateTime startsAt,
    OffsetDateTime endsAt,
    OffsetDateTime orderCutoffAt,
    Money fee,
    boolean available,
    int remainingCapacity) {

  public static SlotResponse from(SlotView slot) {
    return new SlotResponse(
        slot.id(),
        slot.type().name(),
        DukkanTime.toApi(slot.startsAt()),
        DukkanTime.toApi(slot.endsAt()),
        DukkanTime.toApi(slot.orderCutoffAt()),
        Money.eur(slot.feeCents()),
        slot.available(),
        slot.remainingCapacity());
  }
}
