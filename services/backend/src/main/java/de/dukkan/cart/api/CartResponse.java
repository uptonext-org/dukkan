package de.dukkan.cart.api;

import de.dukkan.cart.application.CartLineView;
import de.dukkan.cart.application.CartView;
import de.dukkan.delivery.api.DeliverySlotResponse;
import de.dukkan.promotion.api.PromotionBadge;
import de.dukkan.shared.api.Money;
import de.dukkan.shared.time.DukkanTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Contract schema {@code Cart}. */
record CartResponse(
    UUID id,
    List<Item> items,
    int itemCount,
    Money subtotal,
    Money discountTotal,
    Money deliveryFee,
    Money total,
    Money minimumOrderValue,
    boolean meetsMinimumOrderValue,
    @Nullable Delivery delivery) {

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
      @Nullable PromotionBadge promotion) {

    static Item from(CartLineView line) {
      return new Item(
          line.productId(),
          line.name(),
          line.unitSize(),
          line.imageUrl(),
          Money.eur(line.unitPriceCents()),
          line.quantity(),
          Money.eur(line.subtotalCents()),
          Money.eur(line.discountCents()),
          Money.eur(line.totalCents()),
          PromotionBadge.from(line.promotion()));
    }
  }

  /** Contract schema {@code DeliveryReservation}. */
  record Delivery(DeliverySlotResponse slot, UUID addressId, OffsetDateTime expiresAt) {}

  static CartResponse from(CartView cart) {
    CartView.ReservationView reservation = cart.reservation();
    return new CartResponse(
        cart.id(),
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
            : new Delivery(
                DeliverySlotResponse.from(reservation.slot()),
                reservation.addressId(),
                DukkanTime.toApi(reservation.expiresAt())));
  }
}
