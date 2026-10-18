package de.dukkan.order.application;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Simulated checkout and order history. */
public interface OrderService {

  /**
   * Converts the user's cart into an order. With an idempotency key, repeating the call returns
   * the order created by the first call.
   */
  OrderView placeOrder(UUID userId, @Nullable String idempotencyKey);

  /** The user's orders, newest first. */
  List<OrderView> list(UUID userId);

  OrderView get(UUID userId, UUID orderId);

  /** Cancels a PLACED order before its slot cutoff and releases the slot capacity. */
  OrderView cancel(UUID userId, UUID orderId);
}
