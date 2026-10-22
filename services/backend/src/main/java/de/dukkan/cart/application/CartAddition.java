package de.dukkan.cart.application;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Several products added to a cart at once (e.g. ordering a past order again). */
public final class CartAddition {

  private CartAddition() {}

  public record Line(UUID listingId, int quantity) {}

  public enum SkipReason {
    /** The listing is gone or no longer belongs to the market. */
    REMOVED,
    /** The listing exists but is currently unavailable. */
    UNAVAILABLE,
    /** Added, but fewer than asked because of the per-order maximum. */
    LIMITED
  }

  /** {@code name} is the current name, null when the listing is gone. */
  public record Skipped(UUID listingId, @Nullable String name, SkipReason reason) {}

  public record Result(int addedProducts, List<Skipped> skipped) {}
}
