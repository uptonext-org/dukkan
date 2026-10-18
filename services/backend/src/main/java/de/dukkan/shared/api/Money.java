package de.dukkan.shared.api;

/** API representation of an amount in minor units (cents) and an ISO 4217 currency. */
public record Money(long amountCents, String currency) {

  public static final String EUR = "EUR";

  public static Money eur(long amountCents) {
    return new Money(amountCents, EUR);
  }
}
