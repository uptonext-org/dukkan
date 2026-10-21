package de.dukkan.address.application;

import org.jspecify.annotations.Nullable;

/** Editable fields of an address. */
public record AddressDetails(
    @Nullable String label,
    String street,
    String houseNumber,
    @Nullable String addition,
    String postalCode,
    String city,
    @Nullable String deliveryInstructions) {}
