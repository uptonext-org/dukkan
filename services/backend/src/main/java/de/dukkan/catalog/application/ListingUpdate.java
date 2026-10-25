package de.dukkan.catalog.application;

import org.jspecify.annotations.Nullable;

/**
 * Partial listing update; null fields stay unchanged. The image is changed only when {@code
 * changeImage} is set, and then {@code imageUrl} null removes it. {@code overrides}, when given,
 * replace the market's own details ({@link ListingOverrides#NONE}: the catalog values again).
 */
public record ListingUpdate(
    @Nullable Long priceCents,
    @Nullable Boolean available,
    @Nullable Integer maxOrderQuantity,
    boolean changeImage,
    @Nullable String imageUrl,
    @Nullable ListingOverrides overrides) {

  /** An update that leaves the own details alone. */
  public ListingUpdate(
      @Nullable Long priceCents, @Nullable Boolean available, @Nullable Integer maxOrderQuantity, boolean changeImage, @Nullable String imageUrl) {
    this(priceCents, available, maxOrderQuantity, changeImage, imageUrl, null);
  }

  /** An update that leaves the image and the own details alone. */
  public ListingUpdate(@Nullable Long priceCents, @Nullable Boolean available, @Nullable Integer maxOrderQuantity) {
    this(priceCents, available, maxOrderQuantity, false, null, null);
  }
}
