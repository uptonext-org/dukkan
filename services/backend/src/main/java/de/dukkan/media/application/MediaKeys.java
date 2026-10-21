package de.dukkan.media.application;

import de.dukkan.media.domain.ImageFormat;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

/** Content-addressed keys: lowercase hex SHA-256 of the bytes, a dot and the format's extension. */
public final class MediaKeys {

  public static final Pattern KEY = Pattern.compile("^[0-9a-f]{64}\\.(jpg|png|webp)$");

  private MediaKeys() {}

  public static String of(byte[] bytes, ImageFormat format) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)) + "." + format.extension();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is always available", e);
    }
  }

  public static boolean isValid(String key) {
    return KEY.matcher(key).matches();
  }

  /** The format of a valid key. */
  public static Optional<ImageFormat> format(String key) {
    if (!isValid(key)) {
      return Optional.empty();
    }
    return ImageFormat.forExtension(key.substring(key.indexOf('.') + 1));
  }
}
