package de.dukkan.address.application;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Read model of an address. {@code hubId} is the serving hub, or {@code null} if the postal code
 * is not served.
 */
public record AddressView(
    UUID id,
    @Nullable String label,
    String street,
    String houseNumber,
    @Nullable String addition,
    String postalCode,
    String city,
    String countryCode,
    @Nullable String deliveryInstructions,
    boolean isDefault,
    @Nullable UUID hubId) {

  public boolean serviceable() {
    return hubId != null;
  }
}
