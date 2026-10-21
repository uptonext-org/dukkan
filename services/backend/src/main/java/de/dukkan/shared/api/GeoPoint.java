package de.dukkan.shared.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/** WGS84 coordinates in decimal degrees (contract schema {@code GeoPoint}). */
public record GeoPoint(
    @DecimalMax("90") @DecimalMin("-90") @NotNull Double lat,
    @DecimalMax("180") @DecimalMin("-180") @NotNull Double lng) {

  public static GeoPoint of(double lat, double lng) {
    return new GeoPoint(lat, lng);
  }
}
