package de.dukkan.catalog.application;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Port for the {@code favorite} flag of product summaries, implemented by the favorite module
 * (dependency inversion keeps catalog free of a dependency on favorite).
 */
public interface ListingFavorites {

  /** The subset of {@code listingIds} the user marked as favourite. */
  Set<UUID> favoriteListingIds(UUID userId, Collection<UUID> listingIds);
}
