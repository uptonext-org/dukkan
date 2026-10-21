package de.dukkan.media.domain;

import java.util.Optional;

/** The accepted image formats, recognised by their magic bytes (never by a declared content type). */
public enum ImageFormat {
  JPEG("image/jpeg", "jpg"),
  PNG("image/png", "png"),
  WEBP("image/webp", "webp");

  private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

  private final String contentType;
  private final String extension;

  ImageFormat(String contentType, String extension) {
    this.contentType = contentType;
    this.extension = extension;
  }

  public String contentType() {
    return contentType;
  }

  public String extension() {
    return extension;
  }

  /** The format whose signature starts {@code bytes}, if any. */
  public static Optional<ImageFormat> sniff(byte[] bytes) {
    if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
      return Optional.of(JPEG);
    }
    if (startsWith(bytes, PNG_SIGNATURE, 0)) {
      return Optional.of(PNG);
    }
    if (startsWith(bytes, new byte[] {'R', 'I', 'F', 'F'}, 0) && startsWith(bytes, new byte[] {'W', 'E', 'B', 'P'}, 8)) {
      return Optional.of(WEBP);
    }
    return Optional.empty();
  }

  /** The format stored under a key's extension, if it is one of ours. */
  public static Optional<ImageFormat> forExtension(String extension) {
    for (ImageFormat format : values()) {
      if (format.extension.equals(extension)) {
        return Optional.of(format);
      }
    }
    return Optional.empty();
  }

  static boolean startsWith(byte[] bytes, byte[] prefix, int offset) {
    if (bytes.length < offset + prefix.length) {
      return false;
    }
    for (int i = 0; i < prefix.length; i++) {
      if (bytes[offset + i] != prefix[i]) {
        return false;
      }
    }
    return true;
  }
}
