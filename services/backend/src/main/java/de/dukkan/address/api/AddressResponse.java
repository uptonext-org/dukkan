package de.dukkan.address.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.dukkan.address.application.AddressView;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Contract schema {@code Address}; also used by the order module for address snapshots. */
public record AddressResponse(
    UUID id,
    @Nullable String label,
    String street,
    String houseNumber,
    @Nullable String addition,
    String postalCode,
    String city,
    String countryCode,
    @Nullable String deliveryInstructions,
    @JsonProperty("isDefault") boolean isDefault,
    boolean serviceable) {

  public static AddressResponse from(AddressView a) {
    return new AddressResponse(
        a.id(),
        a.label(),
        a.street(),
        a.houseNumber(),
        a.addition(),
        a.postalCode(),
        a.city(),
        a.countryCode(),
        a.deliveryInstructions(),
        a.isDefault(),
        a.serviceable());
  }
}
