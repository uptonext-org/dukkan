package de.dukkan.order.application;

import de.dukkan.address.application.AddressView;
import de.dukkan.delivery.application.SlotView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read model of an order. */
public record OrderView(
    UUID id,
    String orderNumber,
    String status,
    Instant placedAt,
    List<Item> items,
    long subtotalCents,
    long discountTotalCents,
    long deliveryFeeCents,
    long totalCents,
    SlotView slot,
    AddressView address) {

  public record Item(UUID productId, String name, long unitPriceCents, int quantity, long lineTotalCents) {}
}
