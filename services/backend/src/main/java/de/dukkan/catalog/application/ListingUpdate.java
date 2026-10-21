package de.dukkan.catalog.application;

import org.jspecify.annotations.Nullable;

/**
 * Partial listing update; null fields stay unchanged. The image is changed only when {@code
 * changeImage} is set, and then {@code imageUrl} null removes it.
 */
public record ListingUpdate(
    @Nullable Long priceCents,
    @Nullable Boolean available,
    @Nullable Integer maxOrderQuantity,
    boolean changeImage,
    @Nullable String imageUrl) {

  /** An update that leaves the image alone. */
  public ListingUpdate(@Nullable Long priceCents, @Nullable Boolean available, @Nullable Integer maxOrderQuantity) {
    this(priceCents, available, maxOrderQuantity, false, null);
  }
}
