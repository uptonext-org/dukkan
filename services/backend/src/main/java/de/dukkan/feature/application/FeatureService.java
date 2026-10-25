package de.dukkan.feature.application;

import de.dukkan.feature.infrastructure.FeatureRepository;
import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Platform-wide feature switches that admins set in the portal. */
@Service
public class FeatureService {

  private static final Logger LOG = LoggerFactory.getLogger(FeatureService.class);

  private final FeatureRepository features;
  private final Clock clock;

  public FeatureService(FeatureRepository features, Clock clock) {
    this.features = features;
    this.clock = clock;
  }

  /**
   * Every feature with its effective state. A sub-feature (voice, photo/video) is only on while
   * the shopping list itself is on.
   */
  @Transactional(readOnly = true)
  public Map<Feature, Boolean> all() {
    Map<String, Boolean> stored = features.findAll();
    Map<Feature, Boolean> result = new EnumMap<>(Feature.class);
    for (Feature feature : Feature.values()) {
      result.put(feature, stored.getOrDefault(feature.name(), feature.defaultEnabled()));
    }
    if (!result.get(Feature.SHOPPING_LIST)) {
      result.put(Feature.SHOPPING_LIST_VOICE, false);
      result.put(Feature.SHOPPING_LIST_MEDIA, false);
    }
    return result;
  }

  /** As {@link #all()}, but the switches as stored (sub-features keep their own value). */
  @Transactional(readOnly = true)
  public Map<Feature, Boolean> switches() {
    Map<String, Boolean> stored = features.findAll();
    Map<Feature, Boolean> result = new EnumMap<>(Feature.class);
    for (Feature feature : Feature.values()) {
      result.put(feature, stored.getOrDefault(feature.name(), feature.defaultEnabled()));
    }
    return result;
  }

  public boolean enabled(Feature feature) {
    return all().get(feature);
  }

  @Transactional
  public Map<Feature, Boolean> set(Feature feature, boolean enabled, UUID adminId) {
    features.save(feature.name(), enabled, adminId, clock.instant());
    LOG.info("Feature {} switched {} by admin {}", feature, enabled ? "on" : "off", adminId);
    return switches();
  }
}
