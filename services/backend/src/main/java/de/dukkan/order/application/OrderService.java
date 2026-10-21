package de.dukkan.order.application;

import de.dukkan.address.application.AddressService;
import de.dukkan.address.application.AddressView;
import de.dukkan.cart.application.CartLineView;
import de.dukkan.cart.application.CartService;
import de.dukkan.cart.application.CartView;
import de.dukkan.catalog.application.CatalogService;
import de.dukkan.catalog.application.ListingInfo;
import de.dukkan.fulfilment.application.FulfilmentService;
import de.dukkan.fulfilment.application.SlotView;
import de.dukkan.i18n.application.ContentLocale;
import de.dukkan.identity.application.IdentityService;
import de.dukkan.market.application.MarketInfo;
import de.dukkan.market.application.MarketService;
import de.dukkan.order.domain.AddressSnapshot;
import de.dukkan.order.domain.CustomerOrder;
import de.dukkan.order.domain.OrderItem;
import de.dukkan.order.domain.OrderStatus;
import de.dukkan.order.domain.SlotSnapshot;
import de.dukkan.order.infrastructure.OrderRepository;
import de.dukkan.shared.domain.FulfilmentType;
import de.dukkan.shared.api.GeoPoint;
import de.dukkan.shared.error.BusinessRuleException;
import de.dukkan.shared.error.ConflictException;
import de.dukkan.shared.error.ErrorCodes;
import de.dukkan.shared.events.EventOutbox;
import de.dukkan.shared.error.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Simulated checkout per market, order history, customer cancellation and merchant status changes. */
@Service
@Transactional
public class OrderService {

  private final OrderRepository orders;
  private final CartService carts;
  private final AddressService addresses;
  private final FulfilmentService fulfilment;
  private final MarketService markets;
  private final CatalogService catalog;
  private final IdentityService identity;
  private final EventOutbox outbox;
  private final Clock clock;

  public OrderService(
      OrderRepository orders,
      CartService carts,
      AddressService addresses,
      FulfilmentService fulfilment,
      MarketService markets,
      CatalogService catalog,
      IdentityService identity,
      EventOutbox outbox,
      Clock clock) {
    this.orders = orders;
    this.carts = carts;
    this.addresses = addresses;
    this.fulfilment = fulfilment;
    this.markets = markets;
    this.catalog = catalog;
    this.identity = identity;
    this.outbox = outbox;
    this.clock = clock;
  }

  /**
   * Converts the user's cart in this market into an order and empties that cart. With an
   * idempotency key, repeating the call returns the order created by the first call.
   */
  public OrderView placeOrder(UUID userId, UUID marketId, @Nullable String idempotencyKey, ContentLocale locale) {
    if (idempotencyKey != null) {
      Optional<CustomerOrder> existing = orders.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
      if (existing.isPresent()) {
        CustomerOrder stored = existing.orElseThrow();
        if (!stored.getMarketId().equals(marketId)) {
          throw new ConflictException(
              ErrorCodes.IDEMPOTENCY_KEY_REUSED, "This Idempotency-Key was already used for an order in another market.");
        }
        return view(stored, locale, false);
      }
    }
    MarketInfo market = markets.info(marketId);
    if (!market.isActive()) {
      throw new BusinessRuleException(ErrorCodes.MARKET_NOT_ACTIVE, "This market is currently not taking orders.");
    }
    Instant now = clock.instant();
    CartView cart = carts.getCartForCheckout(userId, marketId, locale);
    if (cart.lines().isEmpty()) {
      throw new BusinessRuleException(ErrorCodes.CART_EMPTY, "The cart is empty.");
    }
    CartView.ReservationView reservation = cart.reservation();
    if (reservation == null) {
      throw new BusinessRuleException(ErrorCodes.SLOT_NOT_RESERVED, "Reserve a delivery or pickup slot before checking out.");
    }
    if (!now.isBefore(reservation.slot().orderCutoffAt())) {
      throw new BusinessRuleException(ErrorCodes.SLOT_CUTOFF_PASSED, "The order cutoff for the reserved slot has passed.");
    }
    if (cart.lines().stream().anyMatch(line -> !line.available())) {
      throw new BusinessRuleException(ErrorCodes.PRODUCT_UNAVAILABLE, "Some products in the cart are no longer available.");
    }
    // Limits and offered fulfilment types may have changed since the cart was filled.
    if (cart.lines().stream().anyMatch(line -> line.quantity() > line.maxOrderQuantity())) {
      throw new BusinessRuleException(
          ErrorCodes.MAX_QUANTITY_EXCEEDED, "Some products exceed their maximum order quantity; please adjust the cart.");
    }
    fulfilment.requireOffered(market, reservation.type());
    if (!cart.meetsMinimumOrderValue()) {
      throw new BusinessRuleException(
          ErrorCodes.MINIMUM_ORDER_NOT_MET,
          "The minimum order value of " + cart.minimumOrderCents() + " cents (after discounts) is not reached.");
    }
    AddressSnapshot address = null;
    if (reservation.type() == FulfilmentType.DELIVERY) {
      AddressView view =
          Optional.ofNullable(reservation.addressId())
              .flatMap(id -> addresses.findOwned(userId, id))
              .orElseThrow(() -> new BusinessRuleException(ErrorCodes.ADDRESS_REQUIRED, "The delivery address no longer exists."));
      fulfilment.checkDeliveryAddress(market, view);
      address = snapshot(view);
    }
    SlotView slot = reservation.slot();
    CustomerOrder order =
        new CustomerOrder(
            new CustomerOrder.Parties(userId, identity.getUser(userId).displayName(), marketId, market.name()),
            OrderNumbers.format(now, orders.nextOrderSequence()),
            reservation.type(),
            now,
            idempotencyKey,
            new CustomerOrder.Totals(cart.subtotalCents(), cart.discountCents(), cart.deliveryFeeCents(), cart.totalCents()),
            new SlotSnapshot(slot.id(), slot.startsAt(), slot.endsAt(), slot.orderCutoffAt()),
            address);
    for (CartLineView line : cart.lines()) {
      order.addItem(
          line.listingId(), line.catalogProductId(), line.name(), line.unitPriceCents(), line.quantity(), line.totalCents());
    }
    orders.save(order);
    // The reservation's capacity unit now belongs to the order; only this market's cart is emptied.
    carts.completeCheckout(userId, marketId);
    outbox.append(OrderEvent.PLACED, order.getId(), event(order, null, "CUSTOMER", now));
    return view(order, locale, false);
  }

  @Transactional(readOnly = true)
  public List<OrderView> list(UUID userId, ContentLocale locale) {
    return views(orders.findByUserIdOrderByPlacedAtDescOrderNumberDesc(userId), locale, false);
  }

  @Transactional(readOnly = true)
  public OrderView get(UUID userId, UUID orderId, ContentLocale locale) {
    return view(owned(userId, orderId), locale, false);
  }

  /** Cancels a PLACED or ACCEPTED order before its slot cutoff and releases the slot capacity. */
  public OrderView cancel(UUID userId, UUID orderId, ContentLocale locale) {
    CustomerOrder order = owned(userId, orderId);
    Instant now = clock.instant();
    if (!order.isCancellable(now)) {
      throw new BusinessRuleException(
          ErrorCodes.ORDER_NOT_CANCELLABLE,
          "Only placed or accepted orders can be cancelled, and only before the slot cutoff.");
    }
    OrderStatus previous = order.getStatus();
    order.cancel(now);
    fulfilment.releaseCapacity(order.getSlot().slotId());
    outbox.append(OrderEvent.STATUS_CHANGED, order.getId(), event(order, previous, "CUSTOMER", now));
    return view(order, locale, false);
  }

  // ------------------------------------------------------------------ merchant

  /** The market an order belongs to, if the order exists. */
  @Transactional(readOnly = true)
  public Optional<UUID> marketOf(UUID orderId) {
    return orders.findById(orderId).map(CustomerOrder::getMarketId);
  }

  /** Orders of a market, newest first, optionally filtered by status (merchant view). */
  @Transactional(readOnly = true)
  public List<OrderView> listForMarket(UUID marketId, @Nullable String status, ContentLocale locale) {
    List<CustomerOrder> found =
        status == null
            ? orders.findByMarketIdOrderByPlacedAtDescOrderNumberDesc(marketId)
            : orders.findByMarketIdAndStatusOrderByPlacedAtDescOrderNumberDesc(marketId, OrderStatus.valueOf(status));
    return views(found, locale, true);
  }

  /** Merchant status change; REJECTED releases the slot capacity. */
  public OrderView advanceStatus(UUID orderId, String target, ContentLocale locale) {
    CustomerOrder order = orders.findById(orderId).orElseThrow(OrderService::orderNotFound);
    OrderStatus next = OrderStatus.valueOf(target);
    OrderStatus previous = order.getStatus();
    if (!order.advanceTo(next)) {
      throw new BusinessRuleException(
          ErrorCodes.INVALID_STATUS_TRANSITION,
          "An order in status " + previous + " (" + order.getFulfilmentType() + ") cannot move to " + next + ".");
    }
    if (next.releasesCapacity() && !previous.releasesCapacity()) {
      fulfilment.releaseCapacity(order.getSlot().slotId());
    }
    outbox.append(OrderEvent.STATUS_CHANGED, order.getId(), event(order, previous, "MERCHANT", clock.instant()));
    return view(order, locale, true);
  }

  // ------------------------------------------------------------------ mapping

  private CustomerOrder owned(UUID userId, UUID orderId) {
    return orders.findByIdAndUserId(orderId, userId).orElseThrow(OrderService::orderNotFound);
  }

  private static OrderEvent event(CustomerOrder order, @Nullable OrderStatus previous, String actor, Instant now) {
    return new OrderEvent(
        order.getId(),
        order.getOrderNumber(),
        order.getMarketId(),
        order.getMarketName(),
        order.getUserId(),
        order.getStatus().name(),
        previous == null ? null : previous.name(),
        order.getFulfilmentType().name(),
        order.getTotals().totalCents(),
        actor,
        now);
  }

  private static NotFoundException orderNotFound() {
    return new NotFoundException("Order not found.");
  }

  private static AddressSnapshot snapshot(AddressView a) {
    GeoPoint location = a.location();
    return new AddressSnapshot(
        a.id(),
        a.label(),
        a.street(),
        a.houseNumber(),
        a.addition(),
        a.postalCode(),
        a.city(),
        a.countryCode(),
        a.deliveryInstructions(),
        location == null ? null : location.lat(),
        location == null ? null : location.lng());
  }

  private OrderView view(CustomerOrder order, ContentLocale locale, boolean merchantView) {
    return views(List.of(order), locale, merchantView).getFirst();
  }

  /** Maps orders with one batched slot lookup and one batched listing lookup (no per-order queries). */
  private List<OrderView> views(List<CustomerOrder> found, ContentLocale locale, boolean merchantView) {
    if (found.isEmpty()) {
      return List.of();
    }
    Map<UUID, SlotView> liveSlots = fulfilment.getSlots(found.stream().map(o -> o.getSlot().slotId()).toList());
    Map<UUID, ListingInfo> listings =
        catalog.listingInfos(
            found.stream().flatMap(o -> o.getItems().stream()).map(OrderItem::getListingId).distinct().toList(), locale);
    return found.stream().map(o -> view(o, merchantView, liveSlots.get(o.getSlot().slotId()), listings)).toList();
  }

  private static OrderView view(
      CustomerOrder order, boolean merchantView, @Nullable SlotView live, Map<UUID, ListingInfo> listings) {
    CustomerOrder.Totals totals = order.getTotals();
    SlotSnapshot s = order.getSlot();
    SlotView slot =
        new SlotView(
            s.slotId(),
            order.getMarketId(),
            order.getFulfilmentType(),
            s.startsAt(),
            s.endsAt(),
            s.orderCutoffAt(),
            totals.deliveryFeeCents(),
            live == null ? 0 : live.remainingCapacity(),
            live != null && live.available());
    AddressSnapshot a = order.getAddress();
    AddressView address =
        a == null
            ? null
            : new AddressView(
                a.addressId(),
                a.label(),
                a.street(),
                a.houseNumber(),
                a.addition(),
                a.postalCode(),
                a.city(),
                a.countryCode(),
                a.deliveryInstructions(),
                a.lat() == null || a.lng() == null ? null : GeoPoint.of(a.lat(), a.lng()),
                false);
    List<OrderView.Item> items =
        order.getItems().stream()
            .map(
                i -> {
                  ListingInfo listing = listings.get(i.getListingId());
                  String name = listing == null || listing.name().isEmpty() ? i.getName() : listing.name();
                  return new OrderView.Item(
                      i.getListingId(), name, i.getUnitPriceCents(), i.getQuantity(), i.getLineTotalCents());
                })
            .toList();
    return new OrderView(
        order.getId(),
        order.getMarketId(),
        order.getOrderNumber(),
        order.getStatus().name(),
        order.getPlacedAt(),
        order.getFulfilmentType(),
        items,
        totals.subtotalCents(),
        totals.discountTotalCents(),
        totals.deliveryFeeCents(),
        totals.totalCents(),
        slot,
        address,
        merchantView ? order.getCustomerName() : null);
  }
}
