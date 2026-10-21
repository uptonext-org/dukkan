package de.dukkan.address.application;

import de.dukkan.shared.api.GeoPoint;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Read model of an address; {@code location} is null when it could not be geocoded. */
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
    @Nullable GeoPoint location,
    boolean isDefault) {}
