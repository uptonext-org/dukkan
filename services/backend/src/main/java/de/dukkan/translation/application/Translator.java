package de.dukkan.translation.application;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Port to a machine translator (a language model). */
public interface Translator {

  /** What the texts are; it changes how they are translated (names stay short, brands untouched). */
  enum TextKind {
    PRODUCT_NAME,
    PRODUCT_DESCRIPTION,
    PROMOTION,
    MARKET_DESCRIPTION
  }

  /**
   * One text to translate: {@code key} is the caller's id, {@code source} the existing versions
   * (locale → text, at least one), {@code targets} the locales to produce.
   */
  record Item(String key, Map<String, String> source, List<String> targets) {}

  boolean enabled();

  /** key → (locale → text) for every item, or empty when the translator failed or declined. */
  Optional<Map<String, Map<String, String>>> translate(TextKind kind, List<Item> items);
}
