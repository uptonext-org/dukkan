package de.dukkan.cart.application;

import static java.util.UUID.randomUUID;

import de.dukkan.address.application.AddressService;
import de.dukkan.address.application.AddressView;
import de.dukkan.cart.domain.Cart;
import de.dukkan.cart.domain.CartItem;
import de.dukkan.cart.infrastructure.CartRepository;
import de.dukkan.catalog.application.CatalogService;
import de.dukkan.catalog.application.ProductView;
import de.dukkan.delivery.application.DeliveryService;
import de.dukkan.delivery.application.SlotView;
import de.dukkan.promotion.application.PricingEngine;
import de.dukkan.shared.config.DukkanProperties;
import de.dukkan.shared.error.BusinessRuleException;
import de.dukkan.shared.error.ErrorCodes;
import de.dukkan.shared.error.NotFoundException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class DefaultCartService implements CartService {

  private final CartRepository carts;
  private final CatalogService catalog;
  private final AddressService addresses;
  private final DeliveryService delivery;
  private final Clock clock;
  private final long minimumOrderCents;

  DefaultCartService(
      CartRepository carts,
      CatalogService catalog,
      AddressService addresses,
      DeliveryService delivery,
      Clock clock,
      DukkanProperties properties) {
    this.carts = carts;
    this.catalog = catalog;
    this.addresses = addresses;
    this.delivery = delivery;
    this.clock = clock;
    this.minimumOrderCents = properties.checkout().minimumOrderCents();
  }

  @Override
  public CartView getCart(UUID userId) {
    Optional<Cart> existing = carts.findByUserId(userId);
    if (existing.isPresent() && !isExpired(existing.orElseThrow())) {
      return view(existing.orElseThrow());
    }
    Cart cart = loadForUpdate(userId);
    releaseIfExpired(cart);
    return view(cart);
  }

  @Override
  public CartView setQuantity(UUID userId, UUID productId, int quantity) {
    ProductView product = catalog.getProduct(productId);
    if (quantity > 0 && !product.available()) {
      throw new BusinessRuleException(ErrorCodes.PRODUCT_UNAVAILABLE, "This product is currently unavailable.");
    }
    if (quantity > product.maxOrderQuantity()) {
      throw new BusinessRuleException(
          ErrorCodes.MAX_QUANTITY_EXCEEDED,
          "At most " + product.maxOrderQuantity() + " units of this product can be ordered.");
    }
    Cart cart = loadForUpdate(userId);
    releaseIfExpired(cart);
    cart.setQuantity(productId, quantity);
    return view(cart);
  }

  @Override
  public CartView removeItem(UUID userId, UUID productId) {
    Cart cart = loadForUpdate(userId);
    releaseIfExpired(cart);
    cart.setQuantity(productId, 0);
    return view(cart);
  }

  @Override
  public void clear(UUID userId) {
    Cart cart = loadForUpdate(userId);
    cart.removeAllItems();
    releaseReservation(cart);
  }

  @Override
  public CartView reserveSlot(UUID userId, UUID slotId, UUID addressId) {
    AddressView address = addresses.getOwned(userId, addressId);
    UUID hubId = address.hubId();
    if (hubId == null) {
      throw new BusinessRuleException(ErrorCodes.ADDRESS_NOT_SERVICEABLE, "We do not deliver to this address yet.");
    }
    Cart cart = loadForUpdate(userId);
    Optional<UUID> previousSlot = cart.getReservedSlotId();
    if (previousSlot.isPresent() && previousSlot.orElseThrow().equals(slotId)) {
      // Same slot: only the address may change; capacity is already held.
      SlotView slot = delivery.getSlot(slotId);
      if (!slot.hubId().equals(hubId)) {
        throw new NotFoundException("Delivery slot not found.");
      }
      if (!clock.instant().isBefore(slot.orderCutoffAt())) {
        throw new BusinessRuleException(ErrorCodes.SLOT_CUTOFF_PASSED, "The order cutoff for this slot has passed.");
      }
      cart.reserve(slotId, addressId, slot.orderCutoffAt());
      return view(cart);
    }
    SlotView slot = delivery.reserveCapacity(slotId, hubId);
    previousSlot.ifPresent(delivery::releaseCapacity);
    cart.reserve(slotId, addressId, slot.orderCutoffAt());
    return view(cart);
  }

  @Override
  public CartView getCartForCheckout(UUID userId) {
    return view(loadForUpdate(userId));
  }

  @Override
  public void completeCheckout(UUID userId) {
    Cart cart = loadForUpdate(userId);
    cart.removeAllItems();
    cart.clearReservation();
  }

  private Cart loadForUpdate(UUID userId) {
    Optional<Cart> cart = carts.findByUserIdForUpdate(userId);
    if (cart.isPresent()) {
      return cart.orElseThrow();
    }
    carts.insertIfAbsent(randomUUID(), userId);
    return carts.findByUserIdForUpdate(userId).orElseThrow();
  }

  private boolean isExpired(Cart cart) {
    return cart.getReservation().filter(r -> !clock.instant().isBefore(r.expiresAt())).isPresent();
  }

  private void releaseIfExpired(Cart cart) {
    if (isExpired(cart)) {
      releaseReservation(cart);
    }
  }

  private void releaseReservation(Cart cart) {
    cart.getReservedSlotId().ifPresent(delivery::releaseCapacity);
    cart.clearReservation();
  }

  private CartView view(Cart cart) {
    List<CartItem> items = cart.getItems();
    Map<UUID, ProductView> products = catalog.findProducts(items.stream().map(CartItem::getProductId).toList());

    CartView.ReservationView reservation =
        cart.getReservation()
            .map(r -> new CartView.ReservationView(delivery.getSlot(r.slotId()), r.addressId(), r.expiresAt()))
            .orElse(null);
    long deliveryFee = reservation == null ? 0 : reservation.slot().feeCents();

    List<PricingEngine.Line> pricingLines = new ArrayList<>();
    List<ProductView> pricedProducts = new ArrayList<>();
    for (CartItem item : items) {
      ProductView product = products.get(item.getProductId());
      if (product != null) {
        pricingLines.add(
            new PricingEngine.Line(product.id(), product.priceCents(), item.getQuantity(), product.promotion()));
        pricedProducts.add(product);
      }
    }
    PricingEngine.Result priced = PricingEngine.price(pricingLines, deliveryFee);

    List<CartLineView> lines = new ArrayList<>();
    for (int i = 0; i < priced.lines().size(); i++) {
      PricingEngine.PricedLine line = priced.lines().get(i);
      ProductView product = pricedProducts.get(i);
      lines.add(
          new CartLineView(
              product.id(),
              product.name(),
              product.unitSize(),
              product.imageUrl(),
              line.unitPriceCents(),
              line.quantity(),
              line.subtotalCents(),
              line.discountCents(),
              line.totalCents(),
              line.promotion(),
              product.available()));
    }
    return new CartView(
        cart.getId(),
        lines,
        priced.itemCount(),
        priced.subtotalCents(),
        priced.discountCents(),
        priced.deliveryFeeCents(),
        priced.totalCents(),
        minimumOrderCents,
        reservation);
  }
}
