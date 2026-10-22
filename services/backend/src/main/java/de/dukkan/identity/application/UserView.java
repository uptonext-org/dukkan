package de.dukkan.identity.application;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Read model of a user (contract schema {@code User}). {@code roles} are sorted role names. */
public record UserView(
    UUID id,
    String email,
    String firstName,
    String lastName,
    List<String> roles,
    @Nullable String preferredLocale,
    boolean emailVerified) {

  public String displayName() {
    return firstName + " " + lastName;
  }
}
