package de.dukkan.shared.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

/** API representation of an amount in minor units (cents) and an ISO 4217 currency. */
public record Money(@PositiveOrZero long amountCents, @NotNull @Pattern(regexp = "^[A-Z]{3}$") String currency) {

  public static final String EUR = "EUR";

  public static Money eur(long amountCents) {
    return new Money(amountCents, EUR);
  }

  @JsonIgnore
  public boolean isEuro() {
    return EUR.equals(currency);
  }
}
