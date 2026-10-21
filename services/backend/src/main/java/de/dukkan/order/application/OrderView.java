package de.dukkan.order.application;

import de.dukkan.address.application.AddressView;
import de.dukkan.fulfilment.application.SlotView;
import de.dukkan.shared.domain.FulfilmentType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Read model of an order; {@code customerName} is only set for merchant views. */
public record OrderView(
    UUID id,
    UUID marketId,
    String orderNumber,
    String status,
    Instant placedAt,
    FulfilmentType fulfilmentType,
    List<Item> items,
    long subtotalCents,
    long discountTotalCents,
    long deliveryFeeCents,
    long totalCents,
    SlotView slot,
    @Nullable AddressView deliveryAddress,
    @Nullable String customerName) {

  /** An order line; the name is localised when the catalog still knows the product. */
  public record Item(UUID productId, String name, long unitPriceCents, int quantity, long lineTotalCents) {}
}
