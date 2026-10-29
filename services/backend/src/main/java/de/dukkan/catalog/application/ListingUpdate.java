package de.dukkan.catalog.application;

import org.jspecify.annotations.Nullable;

/**
 * Partial listing update; null fields stay unchanged. The image is changed only when {@code
 * changeImage} is set, and then {@code imageUrl} null removes it. {@code overrides}, when given,
 * replace the market's own details ({@link ListingOverrides#NONE}: the catalog values again).
 * {@code product}, when given, replaces the details of the market's own (private) product; its
 * {@code imageUrl} is ignored (the image is changed with {@code changeImage}).
 */
public record ListingUpdate(
    @Nullable Long priceCents,
    @Nullable Boolean available,
    @Nullable Integer maxOrderQuantity,
    boolean changeImage,
    @Nullable String imageUrl,
    @Nullable ListingOverrides overrides,
    @Nullable NewProduct product) {

  /** An update that leaves the product details alone. */
  public ListingUpdate(
      @Nullable Long priceCents,
      @Nullable Boolean available,
      @Nullable Integer maxOrderQuantity,
      boolean changeImage,
      @Nullable String imageUrl,
      @Nullable ListingOverrides overrides) {
    this(priceCents, available, maxOrderQuantity, changeImage, imageUrl, overrides, null);
  }

  /** An update that leaves the own details alone. */
  public ListingUpdate(
      @Nullable Long priceCents, @Nullable Boolean available, @Nullable Integer maxOrderQuantity, boolean changeImage, @Nullable String imageUrl) {
    this(priceCents, available, maxOrderQuantity, changeImage, imageUrl, null, null);
  }

  /** An update that leaves the image and the own details alone. */
  public ListingUpdate(@Nullable Long priceCents, @Nullable Boolean available, @Nullable Integer maxOrderQuantity) {
    this(priceCents, available, maxOrderQuantity, false, null, null, null);
  }
}
