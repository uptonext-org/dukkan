package de.dukkan.order.application;

import de.dukkan.cart.application.CartAddition;
import java.util.List;
import java.util.UUID;

/** Result of ordering a past order again: what went into the cart and what did not. */
public record Reorder(UUID marketId, int addedProducts, List<Skipped> skipped) {

  /** {@code name} is the current name, or the name on the order when the product is gone. */
  public record Skipped(UUID productId, String name, CartAddition.SkipReason reason) {}
}
