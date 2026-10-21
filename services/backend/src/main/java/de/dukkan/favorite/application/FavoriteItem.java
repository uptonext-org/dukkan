package de.dukkan.favorite.application;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import de.dukkan.catalog.application.ProductSummary;

/** A product in a favourites group; {@code favoriteReason} is MANUAL or FREQUENTLY_ORDERED. */
public record FavoriteItem(@JsonUnwrapped ProductSummary product, String favoriteReason) {}
