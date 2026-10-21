package de.dukkan.identity.application;

import org.jspecify.annotations.Nullable;

/** Input for creating an account. {@code merchant} adds the MERCHANT role to CUSTOMER. */
public record RegisterCommand(
    String email, String password, String firstName, String lastName, boolean merchant, @Nullable String preferredLocale) {

  public static RegisterCommand customer(String email, String password, String firstName, String lastName) {
    return new RegisterCommand(email, password, firstName, lastName, false, null);
  }

  @Override
  public String toString() {
    return "RegisterCommand[email=" + email + ", password=***, merchant=" + merchant + "]";
  }
}
