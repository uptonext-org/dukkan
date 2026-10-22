package de.dukkan.cart.application;

import static java.util.UUID.randomUUID;

import de.dukkan.address.application.AddressService;
import de.dukkan.address.application.AddressView;
import de.dukkan.cart.domain.Cart;
import de.dukkan.cart.domain.CartItem;
import de.dukkan.cart.infrastructure.CartRepository;
import de.dukkan.catalog.application.CatalogService;
import de.dukkan.catalog.application.ListingInfo;
import de.dukkan.fulfilment.application.DeliveryFee;
import de.dukkan.fulfilment.application.FulfilmentService;
import de.dukkan.fulfilment.application.SlotView;
import de.dukkan.i18n.application.ContentLocale;
import de.dukkan.market.application.MarketInfo;
import de.dukkan.market.application.MarketService;
import de.dukkan.promotion.application.PricingEngine;
import de.dukkan.shared.domain.FulfilmentType;
import de.dukkan.shared.error.BusinessRuleException;
import de.dukkan.shared.error.ErrorCodes;
import de.dukkan.shared.error.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One cart per user and market, its pricing and its fulfilment reservation. Expired reservations
 * (past the slot cutoff) are released when the cart is next read or changed.
 */
@Service
@Transactional
public class CartService {

  private final CartRepository carts;
  private final CatalogService catalog;
  private final MarketService markets;
  private final FulfilmentService fulfilment;
  private final AddressService addresses;
  private final Clock clock;

  public CartService(
      CartRepository carts,
      CatalogService catalog,
      MarketService markets,
      FulfilmentService fulfilment,
      AddressService addresses,
      Clock clock) {
    this.carts = carts;
    this.catalog = catalog;
    this.markets = markets;
    this.fulfilment = fulfilment;
    this.addresses = addresses;
    this.clock = clock;
  }

  /** The cart, created empty if missing (only for active markets). */
  public CartView getCart(UUID userId, UUID marketId, ContentLocale locale) {
    MarketInfo market = markets.info(marketId);
    Optional<Cart> existing = carts.findByUserIdAndMarketId(userId, marketId);
    if (existing.isEmpty() && !market.isActive()) {
      throw new NotFoundException("Market not found.");
    }
    if (existing.isPresent() && !isExpired(existing.orElseThrow())) {
      return view(existing.orElseThrow(), market, locale);
    }
    Cart cart = loadForUpdate(userId, marketId);
    releaseIfExpired(cart);
    return view(cart, market, locale);
  }

  /** Non-empty carts of the user, most recently updated first. */
  @Transactional(readOnly = true)
  public List<CartView> listCarts(UUID userId, ContentLocale locale) {
    List<CartView> result = new ArrayList<>();
    for (Cart cart : carts.findByUserIdOrderByUpdatedAtDesc(userId)) {
      if (!cart.isEmpty()) {
        markets.findInfo(cart.getMarketId()).ifPresent(market -> result.add(view(cart, market, locale)));
      }
    }
    return result;
  }

  /**
   * Sets the quantity of a listing of this market (0 removes it). Fails with {@code NOT_FOUND},
   * {@code MARKET_NOT_ACTIVE}, {@code PRODUCT_UNAVAILABLE} or {@code MAX_QUANTITY_EXCEEDED}.
   */
  public CartView setQuantity(UUID userId, UUID marketId, UUID listingId, int quantity, ContentLocale locale) {
    MarketInfo market = activeForChanges(marketId);
    ListingInfo listing = catalog.listingInfos(List.of(listingId), locale).get(listingId);
    if (listing == null || listing.deleted() || !listing.marketId().equals(marketId)) {
      throw new NotFoundException("Product not found in this market.");
    }
    if (quantity > 0 && !listing.available()) {
      throw new BusinessRuleException(ErrorCodes.PRODUCT_UNAVAILABLE, "This product is currently unavailable.");
    }
    if (quantity > listing.maxOrderQuantity()) {
      throw new BusinessRuleException(
          ErrorCodes.MAX_QUANTITY_EXCEEDED,
          "At most " + listing.maxOrderQuantity() + " units of this product can be ordered.");
    }
    Cart cart = loadForUpdate(userId, marketId);
    releaseIfExpired(cart);
    cart.setQuantity(listingId, quantity, clock.instant());
    releaseIfEmptied(cart);
    return view(cart, market, locale);
  }

  public CartView removeItem(UUID userId, UUID marketId, UUID listingId, ContentLocale locale) {
    MarketInfo market = markets.info(marketId);
    Cart cart = loadForUpdate(userId, marketId);
    releaseIfExpired(cart);
    cart.setQuantity(listingId, 0, clock.instant());
    releaseIfEmptied(cart);
    return view(cart, market, locale);
  }

  /** Empties the cart and releases its reservation. */
  public void clear(UUID userId, UUID marketId) {
    markets.info(marketId);
    Optional<Cart> existing = carts.findForUpdate(userId, marketId);
    if (existing.isEmpty()) {
      return;
    }
    Cart cart = existing.orElseThrow();
    cart.removeAllItems(clock.instant());
    releaseReservation(cart);
  }

  /**
   * Reserves a delivery or pickup slot, replacing (and releasing) any previous reservation.
   * Delivery needs one of the user's addresses inside the market's delivery radius.
   */
  public CartView reserve(
      UUID userId, UUID marketId, FulfilmentType type, UUID slotId, @Nullable UUID addressId, ContentLocale locale) {
    MarketInfo market = activeForChanges(marketId);
    fulfilment.requireOffered(market, type);
    UUID reservedAddress = null;
    if (type == FulfilmentType.DELIVERY) {
      if (addressId == null) {
        throw new BusinessRuleException(ErrorCodes.ADDRESS_REQUIRED, "Delivery needs an address.");
      }
      AddressView address = addresses.getOwned(userId, addressId);
      fulfilment.checkDeliveryAddress(market, address);
      reservedAddress = address.id();
    }
    Cart cart = loadForUpdate(userId, marketId);
    if (cart.isEmpty()) {
      // Capacity is scarce; an empty cart must not hold it (otherwise throwaway accounts can block slots).
      throw new BusinessRuleException(ErrorCodes.CART_EMPTY, "Add products before choosing a time.");
    }
    releaseIfExpired(cart);
    Instant now = clock.instant();
    Optional<Cart.Reservation> previous = cart.getReservation();
    if (previous.isPresent() && previous.orElseThrow().slotId().equals(slotId)) {
      // Same slot: capacity is already held; only the address may change.
      SlotView slot = fulfilment.checkHeldSlot(slotId, market, type);
      cart.reserve(type, slotId, reservedAddress, slot.orderCutoffAt(), now);
      return view(cart, market, locale);
    }
    SlotView slot = fulfilment.reserveCapacity(slotId, market, type);
    previous.ifPresent(p -> fulfilment.releaseCapacity(p.slotId()));
    cart.reserve(type, slotId, reservedAddress, slot.orderCutoffAt(), now);
    return view(cart, market, locale);
  }

  /**
   * Loads the cart for checkout without releasing an expired reservation (so that checkout can
   * report {@code SLOT_CUTOFF_PASSED}). Concurrent checkouts of the same cart conflict.
   */
  public CartView getCartForCheckout(UUID userId, UUID marketId, ContentLocale locale) {
    return view(loadForUpdate(userId, marketId), markets.info(marketId), locale);
  }

  /** Empties the cart after checkout. The reserved capacity is not released: it now belongs to the order. */
  public void completeCheckout(UUID userId, UUID marketId) {
    Cart cart = loadForUpdate(userId, marketId);
    Instant now = clock.instant();
    cart.removeAllItems(now);
    cart.clearReservation(now);
  }

  private MarketInfo activeForChanges(UUID marketId) {
    MarketInfo market = markets.info(marketId);
    if (!market.isActive()) {
      throw new BusinessRuleException(ErrorCodes.MARKET_NOT_ACTIVE, "This market is currently not taking orders.");
    }
    return market;
  }

  private Cart loadForUpdate(UUID userId, UUID marketId) {
    Optional<Cart> cart = carts.findForUpdate(userId, marketId);
    if (cart.isPresent()) {
      return cart.orElseThrow();
    }
    carts.insertIfAbsent(randomUUID(), userId, marketId, clock.instant());
    return carts.findForUpdate(userId, marketId).orElseThrow();
  }

  private boolean isExpired(Cart cart) {
    return cart.getReservation().filter(r -> !clock.instant().isBefore(r.expiresAt())).isPresent();
  }

  private void releaseIfExpired(Cart cart) {
    if (isExpired(cart)) {
      releaseReservation(cart);
    }
  }

  /** A cart that became empty gives its reserved capacity back. */
  private void releaseIfEmptied(Cart cart) {
    if (cart.isEmpty() && cart.getReservation().isPresent()) {
      releaseReservation(cart);
    }
  }

  private void releaseReservation(Cart cart) {
    cart.getReservation().ifPresent(r -> fulfilment.releaseCapacity(r.slotId()));
    cart.clearReservation(clock.instant());
  }

  private CartView view(Cart cart, MarketInfo market, ContentLocale locale) {
    List<CartItem> items = cart.getItems();
    Map<UUID, ListingInfo> listings = catalog.listingInfos(items.stream().map(CartItem::getListingId).toList(), locale);

    List<PricingEngine.Line> pricingLines = new ArrayList<>();
    List<ListingInfo> priced = new ArrayList<>();
    for (CartItem item : items) {
      ListingInfo listing = listings.get(item.getListingId());
      if (listing != null) {
        pricingLines.add(
            new PricingEngine.Line(listing.id(), listing.priceCents(), item.getQuantity(), listing.promotion()));
        priced.add(listing);
      }
    }
    PricingEngine.Result result = PricingEngine.price(pricingLines, 0);

    CartView.ReservationView reservation =
        cart.getReservation()
            .map(r -> new CartView.ReservationView(r.type(), fulfilment.getSlot(r.slotId()), r.addressId(), r.expiresAt()))
            .orElse(null);
    long deliveryFee =
        DeliveryFee.feeCents(
            reservation == null ? null : reservation.type(),
            market.deliveryFeeCents(),
            market.freeDeliveryFromCents(),
            result.merchandiseTotalCents());

    List<CartLineView> lines = new ArrayList<>();
    for (int i = 0; i < result.lines().size(); i++) {
      PricingEngine.PricedLine line = result.lines().get(i);
      ListingInfo listing = priced.get(i);
      lines.add(
          new CartLineView(
              listing.id(),
              listing.catalogProductId(),
              listing.name(),
              listing.unitSize(),
              listing.imageUrl(),
              listing.iconKey(),
              line.unitPriceCents(),
              line.quantity(),
              line.subtotalCents(),
              line.discountCents(),
              line.totalCents(),
              line.promotion() == null ? null : listing.promotionBadge(),
              listing.available(),
              listing.maxOrderQuantity()));
    }
    return new CartView(
        cart.getId(),
        cart.getMarketId(),
        lines,
        result.itemCount(),
        result.subtotalCents(),
        result.discountCents(),
        deliveryFee,
        result.merchandiseTotalCents() + deliveryFee,
        market.minimumOrderCents(),
        reservation,
        cart.getUpdatedAt());
  }

  /** Empties and removes every cart of a deleted account, releasing slot reservations (B-51). */
  public void deleteAll(UUID userId) {
    for (Cart cart : carts.findByUserIdOrderByUpdatedAtDesc(userId)) {
      cart.removeAllItems(clock.instant());
      releaseReservation(cart);
      carts.delete(cart);
    }
  }
}
