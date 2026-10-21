package de.dukkan.geo.application;

import de.dukkan.shared.api.GeoPoint;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Address search and geocoding. Phase 1 uses the built-in Hamburg gazetteer; an external provider
 * can replace the implementation without touching callers.
 */
public interface GeocodingService {

  /** Autocomplete for streets ("Lange Reihe 12"), postal codes and districts, best match first. */
  List<Place> search(String query, int limit);

  /** The nearest known place, if one is reasonably close. */
  Optional<Place> reverse(double lat, double lng);

  /** Coordinates for an address: street within the postal code, else the postal code's centroid. */
  Optional<GeoPoint> geocode(String street, String postalCode, @Nullable String city);
}
