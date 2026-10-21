package de.dukkan.capture.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * What an extractor read from the merchant's input, before it is checked against the known codes.
 * Maps are keyed by locale code; {@code categorySlug} is a category slug such as {@code dairy}.
 */
public record ExtractedProduct(
    Map<String, String> names,
    Map<String, String> descriptions,
    @Nullable String brand,
    @Nullable String unitSize,
    @Nullable BigDecimal netQuantity,
    @Nullable String netUnit,
    @Nullable String categorySlug,
    List<String> cuisineCodes,
    boolean organic,
    List<String> allergens,
    List<String> keywords,
    @Nullable Long priceCents,
    @Nullable String barcode) {

  public ExtractedProduct {
    names = Map.copyOf(names);
    descriptions = Map.copyOf(descriptions);
    cuisineCodes = List.copyOf(cuisineCodes);
    allergens = List.copyOf(allergens);
    keywords = List.copyOf(keywords);
  }

  public static ExtractedProduct empty() {
    return new ExtractedProduct(Map.of(), Map.of(), null, null, null, null, null, List.of(), false, List.of(), List.of(), null, null);
  }
}
