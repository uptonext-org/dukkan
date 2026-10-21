package de.dukkan.catalog.domain;

import de.dukkan.i18n.application.TextNormalizer;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Builds {@code catalog_product.search_text}: the normalised names of all translations, the brand
 * and the keywords, separated by {@code " | "} so that substring matches do not span two terms.
 */
public final class SearchText {

  private SearchText() {}

  public static String build(Collection<String> names, @Nullable String brand, Collection<String> keywords) {
    Set<String> parts = new LinkedHashSet<>();
    names.forEach(n -> add(parts, n));
    add(parts, brand);
    keywords.forEach(k -> add(parts, k));
    return String.join(" | ", parts);
  }

  private static void add(Set<String> parts, @Nullable String value) {
    String normalized = TextNormalizer.normalize(value);
    if (!normalized.isEmpty()) {
      parts.add(normalized);
    }
  }
}
