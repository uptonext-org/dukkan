package de.dukkan.market.application;

import de.dukkan.shared.api.Money;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.Nullable;

/** Contract schema {@code FulfilmentOptions}. */
public record FulfilmentOptions(
    @NotNull Boolean delivery,
    @NotNull Boolean pickup,
    @DecimalMax("50") @DecimalMin("0.5") @Nullable Double deliveryRadiusKm,
    @Nullable @Valid Money deliveryFee,
    @Nullable @Valid Money freeDeliveryFrom,
    @NotNull @Valid Money minimumOrderValue) {}
