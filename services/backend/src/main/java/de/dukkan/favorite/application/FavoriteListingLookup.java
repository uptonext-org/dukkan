package de.dukkan.favorite.application;

import de.dukkan.catalog.application.ListingFavorites;
import de.dukkan.favorite.infrastructure.FavoriteRepository;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Implements catalog's {@link ListingFavorites} port from the favourite tables. */
@Component
public class FavoriteListingLookup implements ListingFavorites {

  private final FavoriteRepository favorites;

  public FavoriteListingLookup(FavoriteRepository favorites) {
    this.favorites = favorites;
  }

  @Override
  public Set<UUID> favoriteListingIds(UUID userId, Collection<UUID> listingIds) {
    return favorites.findProductIdsAmong(userId, listingIds);
  }
}
