package de.dukkan.order.api;

import de.dukkan.address.api.AddressResponse;
import de.dukkan.delivery.api.DeliverySlotResponse;
import de.dukkan.order.application.OrderView;
import de.dukkan.shared.api.Money;
import de.dukkan.shared.time.DukkanTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Contract schema {@code Order}. */
record OrderResponse(
    UUID id,
    String orderNumber,
    String status,
    OffsetDateTime placedAt,
    List<Item> items,
    Money subtotal,
    Money discountTotal,
    Money deliveryFee,
    Money total,
    DeliverySlotResponse slot,
    AddressResponse address) {

  /** Contract schema {@code OrderItem}. */
  record Item(UUID productId, String name, Money unitPrice, int quantity, Money lineTotal) {}

  static OrderResponse from(OrderView o) {
    return new OrderResponse(
        o.id(),
        o.orderNumber(),
        o.status(),
        DukkanTime.toApi(o.placedAt()),
        o.items().stream()
            .map(
                i ->
                    new Item(
                        i.productId(), i.name(), Money.eur(i.unitPriceCents()), i.quantity(), Money.eur(i.lineTotalCents())))
            .toList(),
        Money.eur(o.subtotalCents()),
        Money.eur(o.discountTotalCents()),
        Money.eur(o.deliveryFeeCents()),
        Money.eur(o.totalCents()),
        DeliverySlotResponse.from(o.slot()),
        AddressResponse.from(o.address()));
  }
}
