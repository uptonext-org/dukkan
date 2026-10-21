package de.dukkan.geo.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class GeoMathTest {

  @Test
  void haversineMatchesKnownDistances() {
    // Hamburg Hbf → Hamburg-Altona station
    assertThat(GeoMath.distanceMeters(53.5530, 10.0069, 53.5517, 9.9350)).isCloseTo(4752, within(5.0));
    // Hamburg Rathausmarkt → Munich Marienplatz
    assertThat(GeoMath.distanceMeters(53.5511, 9.9937, 48.1374, 11.5755)).isCloseTo(612_094, within(50.0));
    assertThat(GeoMath.distanceMeters(53.55, 10.0, 53.55, 10.0)).isZero();
  }

  @Test
  void isSymmetric() {
    double there = GeoMath.distanceMeters(53.5551, 10.0128, 53.4580, 9.9800);
    double back = GeoMath.distanceMeters(53.4580, 9.9800, 53.5551, 10.0128);
    assertThat(there).isCloseTo(back, within(1e-6));
  }

  @Test
  void boundingBoxContainsTheWholeCircle() {
    double lat = 53.5551;
    double lng = 10.0128;
    GeoMath.BoundingBox box = GeoMath.boundingBox(lat, lng, 10_000);
    for (int bearing = 0; bearing < 360; bearing += 15) {
      double rad = Math.toRadians(bearing);
      // a point ~9.99 km away in this direction (small-distance approximation)
      double pLat = lat + Math.cos(rad) * 9_990 / 111_195.0;
      double pLng = lng + Math.sin(rad) * 9_990 / (111_195.0 * Math.cos(Math.toRadians(lat)));
      assertThat(GeoMath.distanceMeters(lat, lng, pLat, pLng)).isLessThan(10_000);
      assertThat(pLat).isBetween(box.minLat(), box.maxLat());
      assertThat(pLng).isBetween(box.minLng(), box.maxLng());
    }
  }
}
