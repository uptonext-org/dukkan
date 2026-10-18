package de.dukkan.shared.security;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;

/** Extracts the authenticated user's id (the JWT subject) from the security context. */
public final class CurrentUser {

  private CurrentUser() {}

  public static UUID id(SecurityContext context) {
    Authentication authentication = context.getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
      return UUID.fromString(jwt.getSubject());
    }
    throw new IllegalStateException("No authenticated JWT principal");
  }
}
