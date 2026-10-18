package de.dukkan.order.application;

import de.dukkan.address.application.AddressService;
import de.dukkan.address.application.AddressView;
import de.dukkan.cart.application.CartLineView;
import de.dukkan.cart.application.CartService;
import de.dukkan.cart.application.CartView;
import de.dukkan.delivery.application.DeliveryService;
import de.dukkan.delivery.application.SlotView;
import de.dukkan.order.domain.AddressSnapshot;
import de.dukkan.order.domain.CustomerOrder;
import de.dukkan.order.domain.OrderItem;
import de.dukkan.order.domain.SlotSnapshot;
import de.dukkan.order.infrastructure.OrderRepository;
import de.dukkan.shared.error.BusinessRuleException;
import de.dukkan.shared.error.ErrorCodes;
import de.dukkan.shared.error.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class DefaultOrderService implements OrderService {

  private final OrderRepository orders;
  private final CartService carts;
  private final AddressService addresses;
  private final DeliveryService delivery;
  private final Clock clock;

  DefaultOrderService(
      OrderRepository orders, CartService carts, AddressService addresses, DeliveryService delivery, Clock clock) {
    this.orders = orders;
    this.carts = carts;
    this.addresses = addresses;
    this.delivery = delivery;
    this.clock = clock;
  }

  @Override
  public OrderView placeOrder(UUID userId, @Nullable String idempotencyKey) {
    if (idempotencyKey != null) {
      Optional<CustomerOrder> existing = orders.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
      if (existing.isPresent()) {
        return view(existing.orElseThrow());
      }
    }
    Instant now = clock.instant();
    CartView cart = carts.getCartForCheckout(userId);
    if (cart.lines().isEmpty()) {
      throw new BusinessRuleException(ErrorCodes.CART_EMPTY, "The cart is empty.");
    }
    CartView.ReservationView reservation = cart.reservation();
    if (reservation == null) {
      throw new BusinessRuleException(ErrorCodes.SLOT_NOT_RESERVED, "Reserve a delivery slot before checking out.");
    }
    if (!now.isBefore(reservation.slot().orderCutoffAt())) {
      throw new BusinessRuleException(ErrorCodes.SLOT_CUTOFF_PASSED, "The order cutoff for the reserved slot has passed.");
    }
    if (cart.lines().stream().anyMatch(line -> !line.available())) {
      throw new BusinessRuleException(
          ErrorCodes.PRODUCT_UNAVAILABLE, "Some products in the cart are no longer available.");
    }
    if (!cart.meetsMinimumOrderValue()) {
      throw new BusinessRuleException(
          ErrorCodes.MINIMUM_ORDER_NOT_MET,
          "The minimum order value of " + cart.minimumOrderCents() + " cents (after discounts) is not reached.");
    }
    AddressView address =
        addresses
            .findOwned(userId, reservation.addressId())
            .orElseThrow(
                () ->
                    new BusinessRuleException(
                        ErrorCodes.SLOT_NOT_RESERVED, "The reserved delivery address no longer exists."));

    SlotView slot = reservation.slot();
    CustomerOrder order =
        new CustomerOrder(
            userId,
            OrderNumbers.format(now, orders.nextOrderSequence()),
            now,
            idempotencyKey,
            new CustomerOrder.Totals(
                cart.subtotalCents(), cart.discountCents(), cart.deliveryFeeCents(), cart.totalCents()),
            new SlotSnapshot(slot.id(), slot.startsAt(), slot.endsAt(), slot.orderCutoffAt()),
            new AddressSnapshot(
                address.id(),
                address.label(),
                address.street(),
                address.houseNumber(),
                address.addition(),
                address.postalCode(),
                address.city(),
                address.countryCode(),
                address.deliveryInstructions()));
    for (CartLineView line : cart.lines()) {
      order.addItem(line.productId(), line.name(), line.unitPriceCents(), line.quantity(), line.totalCents());
    }
    orders.save(order);
    // The reservation's capacity unit is transferred to the order; the cart is emptied.
    carts.completeCheckout(userId);
    return view(order);
  }

  @Override
  @Transactional(readOnly = true)
  public List<OrderView> list(UUID userId) {
    return orders.findByUserIdOrderByPlacedAtDescOrderNumberDesc(userId).stream().map(this::view).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public OrderView get(UUID userId, UUID orderId) {
    return view(owned(userId, orderId));
  }

  @Override
  public OrderView cancel(UUID userId, UUID orderId) {
    CustomerOrder order = owned(userId, orderId);
    Instant now = clock.instant();
    if (!order.isCancellable(now)) {
      throw new BusinessRuleException(
          ErrorCodes.ORDER_NOT_CANCELLABLE, "Only placed orders can be cancelled, and only before the slot cutoff.");
    }
    order.cancel(now);
    delivery.releaseCapacity(order.getSlot().slotId());
    return view(order);
  }

  private CustomerOrder owned(UUID userId, UUID orderId) {
    return orders.findByIdAndUserId(orderId, userId).orElseThrow(() -> new NotFoundException("Order not found."));
  }

  private OrderView view(CustomerOrder order) {
    CustomerOrder.Totals totals = order.getTotals();
    SlotSnapshot s = order.getSlot();
    SlotView live = delivery.getSlot(s.slotId());
    SlotView slot =
        new SlotView(
            s.slotId(),
            live.hubId(),
            s.startsAt(),
            s.endsAt(),
            s.orderCutoffAt(),
            totals.deliveryFeeCents(),
            live.remainingCapacity(),
            live.available());
    AddressSnapshot a = order.getAddress();
    AddressView address =
        new AddressView(
            a.addressId(),
            a.label(),
            a.street(),
            a.houseNumber(),
            a.addition(),
            a.postalCode(),
            a.city(),
            a.countryCode(),
            a.deliveryInstructions(),
            false,
            live.hubId());
    List<OrderView.Item> items =
        order.getItems().stream()
            .map(
                (OrderItem i) ->
                    new OrderView.Item(
                        i.getProductId(), i.getName(), i.getUnitPriceCents(), i.getQuantity(), i.getLineTotalCents()))
            .toList();
    return new OrderView(
        order.getId(),
        order.getOrderNumber(),
        order.getStatus().name(),
        order.getPlacedAt(),
        items,
        totals.subtotalCents(),
        totals.discountTotalCents(),
        totals.deliveryFeeCents(),
        totals.totalCents(),
        slot,
        address);
  }
}
