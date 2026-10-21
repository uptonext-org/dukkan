package de.dukkan.geo.application;

import de.dukkan.shared.api.GeoPoint;

/** Great-circle distances (Haversine on a sphere with the mean Earth radius) and bounding boxes. */
public final class GeoMath {

  public static final double EARTH_RADIUS_METERS = 6_371_008.8;
  private static final double METERS_PER_DEGREE_LAT = Math.PI * EARTH_RADIUS_METERS / 180.0;

  /** A lat/lng rectangle that contains every point within the radius of the centre. */
  public record BoundingBox(double minLat, double maxLat, double minLng, double maxLng) {}

  private GeoMath() {}

  public static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
    double dLat = Math.toRadians(lat2 - lat1);
    double dLng = Math.toRadians(lng2 - lng1);
    double a =
        Math.pow(Math.sin(dLat / 2), 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(dLng / 2), 2);
    return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1.0, Math.sqrt(a)));
  }

  public static double distanceMeters(GeoPoint a, GeoPoint b) {
    return distanceMeters(a.lat(), a.lng(), b.lat(), b.lng());
  }

  public static BoundingBox boundingBox(double lat, double lng, double radiusMeters) {
    double dLat = radiusMeters / METERS_PER_DEGREE_LAT;
    double cosLat = Math.cos(Math.toRadians(lat));
    double dLng = cosLat < 1e-6 ? 180 : Math.min(180, dLat / cosLat);
    return new BoundingBox(lat - dLat, lat + dLat, lng - dLng, lng + dLng);
  }
}
