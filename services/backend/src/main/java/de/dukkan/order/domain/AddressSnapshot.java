package de.dukkan.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Copy of the delivery address taken when the order was placed. */
@Embeddable
public record AddressSnapshot(
    @Column(name = "address_id", nullable = false) UUID addressId,
    @Column(name = "address_label") @Nullable String label,
    @Column(name = "address_street", nullable = false) String street,
    @Column(name = "address_house_number", nullable = false) String houseNumber,
    @Column(name = "address_addition") @Nullable String addition,
    @Column(name = "address_postal_code", nullable = false) String postalCode,
    @Column(name = "address_city", nullable = false) String city,
    @Column(name = "address_country_code", nullable = false) String countryCode,
    @Column(name = "address_delivery_instructions") @Nullable String deliveryInstructions) {}
