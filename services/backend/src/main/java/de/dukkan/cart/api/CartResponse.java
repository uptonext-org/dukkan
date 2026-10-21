package de.dukkan.cart.api;

import de.dukkan.cart.application.CartLineView;
import de.dukkan.cart.application.CartView;
import de.dukkan.catalog.application.PromotionBadge;
import de.dukkan.fulfilment.api.SlotResponse;
import de.dukkan.market.application.MarketSummary;
import de.dukkan.shared.api.Money;
import de.dukkan.shared.time.DukkanTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Contract schema {@code Cart}. */
record CartResponse(
    UUID id,
    MarketSummary market,
    List<Item> items,
    int itemCount,
    Money subtotal,
    Money discountTotal,
    Money deliveryFee,
    Money total,
    Money minimumOrderValue,
    boolean meetsMinimumOrderValue,
    @Nullable Reservation fulfilment) {

  /** Contract schema {@code CartItem}. */
  record Item(
      UUID productId,
      String name,
      String unitSize,
      @Nullable String imageUrl,
      Money unitPrice,
      int quantity,
      Money lineSubtotal,
      Money lineDiscount,
      Money lineTotal,
      @Nullable PromotionBadge promotion,
      boolean available,
      int maxOrderQuantity) {

    static Item from(CartLineView line) {
      return new Item(
          line.listingId(),
          line.name(),
          line.unitSize(),
          line.imageUrl(),
          Money.eur(line.unitPriceCents()),
          line.quantity(),
          Money.eur(line.subtotalCents()),
          Money.eur(line.discountCents()),
          Money.eur(line.totalCents()),
          line.promotion(),
          line.available(),
          line.maxOrderQuantity());
    }
  }

  /** Contract schema {@code FulfilmentReservation}. */
  record Reservation(String type, SlotResponse slot, @Nullable UUID addressId, OffsetDateTime expiresAt) {}

  /** Contract schema {@code CartSummary}. */
  record Summary(UUID cartId, MarketSummary market, int itemCount, Money total, OffsetDateTime updatedAt) {
    static Summary from(CartView cart, MarketSummary market) {
      return new Summary(cart.id(), market, cart.itemCount(), Money.eur(cart.totalCents()), DukkanTime.toApi(cart.updatedAt()));
    }
  }

  static CartResponse from(CartView cart, MarketSummary market) {
    CartView.ReservationView reservation = cart.reservation();
    return new CartResponse(
        cart.id(),
        market,
        cart.lines().stream().map(Item::from).toList(),
        cart.itemCount(),
        Money.eur(cart.subtotalCents()),
        Money.eur(cart.discountCents()),
        Money.eur(cart.deliveryFeeCents()),
        Money.eur(cart.totalCents()),
        Money.eur(cart.minimumOrderCents()),
        cart.meetsMinimumOrderValue(),
        reservation == null
            ? null
            : new Reservation(
                reservation.type().name(),
                SlotResponse.from(reservation.slot()),
                reservation.addressId(),
                DukkanTime.toApi(reservation.expiresAt())));
  }
}
