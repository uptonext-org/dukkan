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

  /** Original keys and their resized variants ({@code <hash>-w320.jpg}, B-32d). */
  private static final Pattern STORAGE_KEY = Pattern.compile("^[0-9a-f]{64}(-w[0-9]{2,4})?\\.(jpg|png|webp)$");

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

  /** An original or variant key, as accepted by {@link MediaStorage} implementations. */
  public static boolean isStorageKey(String key) {
    return STORAGE_KEY.matcher(key).matches();
  }

  /** The key of the variant of an original at the given width, in the given format. */
  public static String variant(String key, int width, ImageFormat format) {
    return key.substring(0, key.indexOf('.')) + "-w" + width + "." + format.extension();
  }

  /** The format of a valid key (original or variant). */
  public static Optional<ImageFormat> format(String key) {
    if (!isStorageKey(key)) {
      return Optional.empty();
    }
    return ImageFormat.forExtension(key.substring(key.indexOf('.') + 1));
  }
}
