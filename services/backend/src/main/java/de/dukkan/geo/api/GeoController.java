package de.dukkan.geo.api;

import de.dukkan.geo.application.GeocodingService;
import de.dukkan.geo.application.Place;
import de.dukkan.shared.error.NotFoundException;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/v1/geo")
@RestController
final class GeoController {

  private final GeocodingService geocoding;

  GeoController(GeocodingService geocoding) {
    this.geocoding = geocoding;
  }

  @GetMapping("/search")
  List<Place> search(
      @RequestParam @Size(min = 2, max = 120) String q,
      @Max(10) @Min(1) @RequestParam(defaultValue = "5") int limit) {
    return geocoding.search(q, limit);
  }

  @GetMapping("/reverse")
  Place reverse(
      @DecimalMax("90") @DecimalMin("-90") @RequestParam double lat,
      @DecimalMax("180") @DecimalMin("-180") @RequestParam double lng) {
    return geocoding.reverse(lat, lng).orElseThrow(() -> new NotFoundException("No known place near these coordinates."));
  }
}
