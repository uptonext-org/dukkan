package de.dukkan.delivery.api;

import de.dukkan.delivery.application.SlotView;
import de.dukkan.shared.api.Money;
import de.dukkan.shared.time.DukkanTime;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Contract schema {@code DeliverySlot}; reused by the cart and order APIs. */
public record DeliverySlotResponse(
    UUID id,
    OffsetDateTime startsAt,
    OffsetDateTime endsAt,
    OffsetDateTime orderCutoffAt,
    Money fee,
    boolean available,
    int remainingCapacity) {

  public static DeliverySlotResponse from(SlotView slot) {
    return new DeliverySlotResponse(
        slot.id(),
        DukkanTime.toApi(slot.startsAt()),
        DukkanTime.toApi(slot.endsAt()),
        DukkanTime.toApi(slot.orderCutoffAt()),
        Money.eur(slot.feeCents()),
        slot.available(),
        slot.remainingCapacity());
  }
}
