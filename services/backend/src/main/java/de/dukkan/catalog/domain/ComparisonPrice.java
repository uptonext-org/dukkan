package de.dukkan.catalog.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Price per reference unit (Grundpreis), rounded half-up to whole cents. */
public record ComparisonPrice(long amountCents, String unit) {

  public static Optional<ComparisonPrice> of(long priceCents, @Nullable BigDecimal netQuantity, @Nullable String netUnit) {
    if (netQuantity == null || netUnit == null || netQuantity.signum() <= 0) {
      return Optional.empty();
    }
    long perUnit = BigDecimal.valueOf(priceCents).divide(netQuantity, 0, RoundingMode.HALF_UP).longValueExact();
    return Optional.of(new ComparisonPrice(perUnit, netUnit));
  }
}
