package de.dukkan.address.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.dukkan.address.domain.AddressDetails;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** Body of create/update address requests. */
record AddressRequest(
    @Nullable @Size(max = 50) String label,
    @NotBlank @Size(min = 1, max = 120) String street,
    @NotBlank @Size(min = 1, max = 10) String houseNumber,
    @Nullable @Size(max = 50) String addition,
    @NotNull @Pattern(regexp = "^[0-9]{5}$", message = "must be a 5-digit German postal code") String postalCode,
    @NotBlank @Size(min = 1, max = 80) String city,
    @Nullable @Size(max = 300) String deliveryInstructions,
    @JsonProperty("isDefault") @Nullable Boolean isDefault) {

  AddressDetails toDetails() {
    return new AddressDetails(
        blankToNull(label),
        street.strip(),
        houseNumber.strip(),
        blankToNull(addition),
        postalCode,
        city.strip(),
        blankToNull(deliveryInstructions));
  }

  boolean makeDefault() {
    return Boolean.TRUE.equals(isDefault);
  }

  private static @Nullable String blankToNull(@Nullable String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
