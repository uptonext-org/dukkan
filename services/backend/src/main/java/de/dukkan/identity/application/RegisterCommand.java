package de.dukkan.identity.application;

/** Input for creating an account. */
public record RegisterCommand(String email, String password, String firstName, String lastName) {

  @Override
  public String toString() {
    return "RegisterCommand[email=" + email + ", password=***]";
  }
}
