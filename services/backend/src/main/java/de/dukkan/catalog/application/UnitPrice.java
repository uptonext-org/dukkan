package de.dukkan.catalog.application;

import de.dukkan.shared.api.Money;

/** Contract schema {@code UnitPrice} (Grundpreis per kg, l or piece). */
public record UnitPrice(Money price, String unit) {}
