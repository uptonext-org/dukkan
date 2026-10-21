package de.dukkan.market.application;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Contract schema {@code OpeningHours}: {@code HH:mm} strings, day 1 = Monday. */
public record OpeningHours(
    @Max(7) @Min(1) @NotNull Integer dayOfWeek,
    @NotNull @Pattern(regexp = "^[0-2][0-9]:[0-5][0-9]$") String opensAt,
    @NotNull @Pattern(regexp = "^[0-2][0-9]:[0-5][0-9]$") String closesAt) {}
