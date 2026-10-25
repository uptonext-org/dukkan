package de.dukkan.feature.api;

import de.dukkan.feature.application.Feature;
import de.dukkan.feature.application.FeatureService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public: which switchable functions the apps show (effective state). */
@RestController
final class FeatureController {

  private final FeatureService features;

  FeatureController(FeatureService features) {
    this.features = features;
  }

  @GetMapping("/api/v1/features")
  Map<Feature, Boolean> features() {
    return features.all();
  }
}
