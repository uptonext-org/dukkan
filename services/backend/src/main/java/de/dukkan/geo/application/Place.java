package de.dukkan.geo.application;

import de.dukkan.shared.api.GeoPoint;
import org.jspecify.annotations.Nullable;

/** A geocoded place (contract schema {@code Place}). */
public record Place(
    String label,
    @Nullable String street,
    @Nullable String houseNumber,
    @Nullable String postalCode,
    @Nullable String district,
    String city,
    GeoPoint location) {}
