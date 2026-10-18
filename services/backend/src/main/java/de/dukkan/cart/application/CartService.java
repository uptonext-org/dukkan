package de.dukkan.cart.application;

import java.util.UUID;

/** The user's active cart, its pricing and its delivery slot reservation. */
public interface CartService {

  /** Returns the cart, creating an empty one if needed. Expired reservations are released. */
  CartView getCart(UUID userId);

  /**
   * Sets the quantity of a product (0 removes it). Fails with {@code NOT_FOUND}, {@code
   * PRODUCT_UNAVAILABLE} or {@code MAX_QUANTITY_EXCEEDED}.
   */
  CartView setQuantity(UUID userId, UUID productId, int quantity);

  CartView removeItem(UUID userId, UUID productId);

  /** Empties the cart and releases the slot reservation. */
  void clear(UUID userId);

  /** Reserves a slot for one of the user's addresses, replacing (and releasing) any previous one. */
  CartView reserveSlot(UUID userId, UUID slotId, UUID addressId);

  /**
   * Loads the cart for checkout without releasing an expired reservation (so that checkout can
   * report {@code SLOT_CUTOFF_PASSED}). Concurrent checkouts of the same cart conflict.
   */
  CartView getCartForCheckout(UUID userId);

  /**
   * Empties the cart after a successful checkout. The reserved capacity is <em>not</em> released:
   * it now belongs to the order.
   */
  void completeCheckout(UUID userId);
}
