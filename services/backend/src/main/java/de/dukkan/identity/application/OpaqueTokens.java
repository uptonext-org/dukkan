package de.dukkan.identity.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Random bearer secrets for refresh and password reset tokens: 32 bytes from {@link SecureRandom},
 * URL-safe Base64 without padding. Only their SHA-256 hash is stored.
 */
final class OpaqueTokens {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int BYTES = 32;

  private OpaqueTokens() {}

  static String generate() {
    byte[] bytes = new byte[BYTES];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  static byte[] hash(String token) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
