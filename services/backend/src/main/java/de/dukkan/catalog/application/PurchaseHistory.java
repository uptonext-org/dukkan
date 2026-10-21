package de.dukkan.catalog.application;

import java.util.List;
import java.util.UUID;

/**
 * Port for "frequently ordered" listings, implemented by the order module (dependency inversion
 * keeps catalog free of a dependency on order).
 */
public interface PurchaseHistory {

  /** Listings in ≥ 2 of the user's non-cancelled orders in the market, most ordered first. */
  List<UUID> frequentlyOrderedListingIds(UUID userId, UUID marketId);
}
