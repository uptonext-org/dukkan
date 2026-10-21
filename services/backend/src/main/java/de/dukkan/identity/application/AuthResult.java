package de.dukkan.identity.application;

import java.time.Duration;

/** Result of a successful sign-in: a short-lived access token and a rotating refresh token. */
public record AuthResult(String accessToken, Duration expiresIn, String refreshToken, Duration refreshExpiresIn, UserView user) {

  @Override
  public String toString() {
    return "AuthResult[user=" + user.id() + ", tokens=***]";
  }
}
