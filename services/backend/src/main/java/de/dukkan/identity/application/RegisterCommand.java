package de.dukkan.identity.application;

import org.jspecify.annotations.Nullable;

/**
 * Input for creating an account. {@code merchant} adds the MERCHANT role to CUSTOMER;
 * {@code acceptedTermsVersion} is the terms version the user agreed to (required when the platform sets one).
 */
public record RegisterCommand(
    String email,
    String password,
    String firstName,
    String lastName,
    boolean merchant,
    @Nullable String preferredLocale,
    @Nullable String acceptedTermsVersion) {

  public static RegisterCommand customer(String email, String password, String firstName, String lastName) {
    return new RegisterCommand(email, password, firstName, lastName, false, null, null);
  }

  @Override
  public String toString() {
    return "RegisterCommand[email=" + email + ", password=***, merchant=" + merchant + "]";
  }
}
