package de.dukkan.catalog.application;

import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Contract schema {@code ListingOverrides}: a market's own details for one listing, in the market's
 * language and shown in every language. They never change the shared catalog product; a {@code
 * null} field shows the catalog value.
 */
public record ListingOverrides(
    @Nullable @Size(max = 200) String name,
    @Nullable @Size(max = 2000) String description,
    @Nullable @Size(max = 40) String unitSize,
    @Nullable @Size(max = 80) String brand) {

  public static final ListingOverrides NONE = new ListingOverrides(null, null, null, null);

  /** Blank fields become {@code null}; the rest is trimmed. */
  public ListingOverrides normalized() {
    return new ListingOverrides(clean(name), clean(description), clean(unitSize), clean(brand));
  }

  public boolean isEmpty() {
    return name == null && description == null && unitSize == null && brand == null;
  }

  private static @Nullable String clean(@Nullable String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
