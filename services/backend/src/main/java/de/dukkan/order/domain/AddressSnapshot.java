package de.dukkan.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Copy of the delivery address taken when the order was placed (null for pickup orders). */
@Embeddable
public record AddressSnapshot(
    @Column(name = "address_id") UUID addressId,
    @Column(name = "address_label") @Nullable String label,
    @Column(name = "address_street") String street,
    @Column(name = "address_house_number") String houseNumber,
    @Column(name = "address_addition") @Nullable String addition,
    @Column(name = "address_postal_code") String postalCode,
    @Column(name = "address_city") String city,
    @Column(name = "address_country_code") String countryCode,
    @Column(name = "address_delivery_instructions") @Nullable String deliveryInstructions,
    @Column(name = "address_lat") @Nullable Double lat,
    @Column(name = "address_lng") @Nullable Double lng) {}
