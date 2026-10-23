package de.dukkan.order.api;

import de.dukkan.address.api.AddressResponse;
import de.dukkan.fulfilment.api.SlotResponse;
import de.dukkan.market.application.MarketSummary;
import de.dukkan.order.application.OrderView;
import de.dukkan.shared.api.Money;
import de.dukkan.shared.time.DukkanTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Contract schema {@code Order}; also used by the merchant API. */
public record OrderResponse(
    UUID id,
    String orderNumber,
    String status,
    OffsetDateTime placedAt,
    MarketSummary market,
    String fulfilmentType,
    List<Item> items,
    Money subtotal,
    Money discountTotal,
    Money deliveryFee,
    Money total,
    SlotResponse slot,
    @Nullable AddressResponse deliveryAddress,
    @Nullable String customerName,
    @Nullable Money originalTotal) {

  /** Contract schema {@code OrderItem}; {@code fulfilledQuantity} is set for missing items. */
  public record Item(
      int lineNo, UUID productId, String name, Money unitPrice, int quantity, @Nullable Integer fulfilledQuantity, Money lineTotal) {}

  public static OrderResponse from(OrderView o, MarketSummary market) {
    return new OrderResponse(
        o.id(),
        o.orderNumber(),
        o.status(),
        DukkanTime.toApi(o.placedAt()),
        market,
        o.fulfilmentType().name(),
        o.items().stream()
            .map(
                i ->
                    new Item(
                        i.lineNo(),
                        i.productId(),
                        i.name(),
                        Money.eur(i.unitPriceCents()),
                        i.quantity(),
                        i.fulfilledQuantity(),
                        Money.eur(i.lineTotalCents())))
            .toList(),
        Money.eur(o.subtotalCents()),
        Money.eur(o.discountTotalCents()),
        Money.eur(o.deliveryFeeCents()),
        Money.eur(o.totalCents()),
        SlotResponse.from(o.slot()),
        o.deliveryAddress() == null ? null : AddressResponse.from(o.deliveryAddress()),
        o.customerName(),
        o.originalTotalCents() == null ? null : Money.eur(o.originalTotalCents()));
  }
}
