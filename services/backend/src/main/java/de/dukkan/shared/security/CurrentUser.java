package de.dukkan.shared.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;

/** Extracts the authenticated user's id (the JWT subject) from the security context. */
public final class CurrentUser {

  private CurrentUser() {}

  /** The signed-in user's id; only call on endpoints that require authentication. */
  public static UUID id(SecurityContext context) {
    return optionalId(context).orElseThrow(() -> new IllegalStateException("No authenticated JWT principal"));
  }

  /** The signed-in user's id, or empty for anonymous callers of public endpoints. */
  public static Optional<UUID> optionalId(SecurityContext context) {
    Authentication authentication = context.getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
      return Optional.of(UUID.fromString(jwt.getSubject()));
    }
    return Optional.empty();
  }
}
