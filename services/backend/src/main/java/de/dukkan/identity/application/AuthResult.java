package de.dukkan.identity.application;

import java.time.Duration;

/** Result of a successful registration or login. */
public record AuthResult(String accessToken, Duration expiresIn, UserView user) {}
