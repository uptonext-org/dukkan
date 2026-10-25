package de.dukkan.market.application;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

/** Contract schema {@code MerchantOrderSettings}: the settings plus the platform defaults for empty fields. */
public record OrderSettingsView(@JsonUnwrapped OrderSettings settings, OrderSettings.Defaults defaults) {}
