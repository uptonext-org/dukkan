package de.dukkan.order.application;

import de.dukkan.address.application.AddressService;
import de.dukkan.address.application.AddressView;
import de.dukkan.cart.application.CartAddition;
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
import io.micrometer.core.instrument.MeterRegistry;
import de.dukkan.shared.error.InvalidRequestException;
import de.dukkan.shared.error.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
  private final MeterRegistry meters;
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
      MeterRegistry meters,
      Clock clock) {
    this.orders = orders;
    this.carts = carts;
    this.addresses = addresses;
    this.fulfilment = fulfilment;
    this.markets = markets;
    this.catalog = catalog;
    this.identity = identity;
    this.outbox = outbox;
    this.meters = meters;
    this.clock = clock;
  }

  /**
   * Converts the user's cart in this market into an order and empties that cart. With an
   * idempotency key, repeating the call returns the order created by the first call.
   */
  public OrderView placeOrder(UUID userId, UUID marketId, @Nullable String idempotencyKey, ContentLocale locale) {
    identity.requireVerifiedEmail(userId);
    if (idempotencyKey != null) {
      orders.lockIdempotencyKey(userId, idempotencyKey);
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
    FulfilmentService.requireAcceptsOrders(market, now);
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
    // The reservation may be days old: a holiday set since then closes that day (SLOT_UNAVAILABLE).
    fulfilment.checkHeldSlot(reservation.slot().id(), market, reservation.type());
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
          line.listingId(),
          line.catalogProductId(),
          line.name(),
          line.unitPriceCents(),
          line.quantity(),
          line.totalCents(),
          ChargedPrice.snapshot(line.appliedRule()));
    }
    orders.save(order);
    // The reservation's capacity unit now belongs to the order; only this market's cart is emptied.
    carts.completeCheckout(userId, marketId);
    outbox.append(OrderEvent.PLACED, order.getId(), event(order, null, "CUSTOMER", now));
    meters.counter("dukkan.orders.placed", "fulfilment", order.getFulfilmentType().name()).increment();
    if (market.ordering().autoAccept() && order.advanceTo(OrderStatus.ACCEPTED)) {
      // The market accepts every order itself; the customer is told as if the merchant had clicked.
      outbox.append(OrderEvent.STATUS_CHANGED, order.getId(), event(order, OrderStatus.PLACED, "MERCHANT", now));
      meters.counter("dukkan.orders.status_changes", "status", OrderStatus.ACCEPTED.name(), "by", "auto").increment();
    }
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
    meters.counter("dukkan.orders.status_changes", "status", order.getStatus().name(), "by", "customer").increment();
    return view(order, locale, false);
  }

  /**
   * Puts the products of one of the user's orders into the cart of its market again, on top of
   * what is there, at today's prices. Unavailable and removed products are skipped and reported.
   */
  public Reorder reorder(UUID userId, UUID orderId, ContentLocale locale) {
    CustomerOrder order = owned(userId, orderId);
    Map<UUID, Integer> quantities = new LinkedHashMap<>();
    Map<UUID, String> names = new HashMap<>();
    for (OrderItem item : order.getItems()) {
      quantities.merge(item.getListingId(), item.getQuantity(), Integer::sum);
      names.putIfAbsent(item.getListingId(), item.getName());
    }
    List<CartAddition.Line> lines = quantities.entrySet().stream().map(e -> new CartAddition.Line(e.getKey(), e.getValue())).toList();
    CartAddition.Result result = carts.addAll(userId, order.getMarketId(), lines, locale);
    List<Reorder.Skipped> skipped =
        result.skipped().stream()
            .map(s -> new Reorder.Skipped(s.listingId(), s.name() != null ? s.name() : names.getOrDefault(s.listingId(), ""), s.reason()))
            .toList();
    return new Reorder(order.getMarketId(), result.addedProducts(), skipped);
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
    meters.counter("dukkan.orders.status_changes", "status", order.getStatus().name(), "by", "merchant").increment();
    return view(order, locale, true);
  }

  /**
   * Missing items while packing: sets the units handed over per line (0 = missing) while the order
   * is placed or accepted; totals are recomputed and the customer is told (event {@code
   * order.items_adjusted}). Lines not mentioned stay as they are.
   */
  public OrderView adjustItems(UUID orderId, Map<Integer, Integer> unitsByLine, ContentLocale locale) {
    CustomerOrder order = orders.findById(orderId).orElseThrow(OrderService::orderNotFound);
    if (!order.isAdjustable()) {
      throw new BusinessRuleException(
          ErrorCodes.ORDER_NOT_ADJUSTABLE, "Items can only be changed before the order is ready or on its way.");
    }
    for (Map.Entry<Integer, Integer> entry : unitsByLine.entrySet()) {
      OrderItem item =
          order.getItems().stream()
              .filter(i -> i.getLineNo() == entry.getKey())
              .findFirst()
              .orElseThrow(() -> new InvalidRequestException("items", "UnknownLine", "line " + entry.getKey() + " is not part of the order"));
      if (entry.getValue() < 0 || entry.getValue() > item.getQuantity()) {
        throw new InvalidRequestException("items", "Range", "line " + entry.getKey() + ": between 0 and " + item.getQuantity());
      }
    }
    long before = order.getTotals().totalCents();
    Instant now = clock.instant();
    int changed;
    try {
      changed = order.adjustItems(unitsByLine, ChargedPrice::of, now);
    } catch (IllegalStateException e) {
      throw new BusinessRuleException(ErrorCodes.ORDER_ADJUSTMENT_EMPTY, "Nothing would be left; reject the order instead.");
    }
    if (changed > 0) {
      int shortItems = (int) order.getItems().stream().filter(i -> i.getFulfilledQuantity() != null).count();
      outbox.append(OrderEvent.ITEMS_ADJUSTED, order.getId(), event(order, null, "MERCHANT", now, before, shortItems));
      meters.counter("dukkan.orders.items_adjusted").increment();
    }
    return view(order, locale, true);
  }

  // ------------------------------------------------------------------ mapping

  private CustomerOrder owned(UUID userId, UUID orderId) {
    return orders.findByIdAndUserId(orderId, userId).orElseThrow(OrderService::orderNotFound);
  }

  private static OrderEvent event(CustomerOrder order, @Nullable OrderStatus previous, String actor, Instant now) {
    return event(order, previous, actor, now, null, null);
  }

  private static OrderEvent event(
      CustomerOrder order,
      @Nullable OrderStatus previous,
      String actor,
      Instant now,
      @Nullable Long previousTotalCents,
      @Nullable Integer shortItems) {
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
        now,
        previousTotalCents,
        shortItems);
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
                      i.getLineNo(),
                      i.getListingId(),
                      name,
                      i.getUnitPriceCents(),
                      i.getQuantity(),
                      i.getChargedTotalCents(),
                      i.getFulfilledQuantity());
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
        merchantView ? order.getCustomerName() : null,
        order.getOriginalTotalCents());
  }

  /** True while an order of the user is not finished (placed, accepted, ready or on the way). */
  @Transactional(readOnly = true)
  public boolean hasOpenOrders(UUID userId) {
    return orders.existsByUserIdAndStatusIn(
        userId, List.of(OrderStatus.PLACED, OrderStatus.ACCEPTED, OrderStatus.READY_FOR_PICKUP, OrderStatus.OUT_FOR_DELIVERY));
  }

  /** Removes the customer's personal data from their orders (account deletion, B-51). */
  public void anonymizeCustomer(UUID userId) {
    orders.anonymizeCustomer(userId);
  }
}
