package de.dukkan.media.domain;

import java.util.Optional;

/**
 * Reads the canvas size from a WebP file's RIFF header (ImageIO has no WebP reader in the JDK).
 * Supports the three bitstream chunks: {@code VP8 } (lossy), {@code VP8L} (lossless) and {@code
 * VP8X} (extended: alpha, animation, metadata). Only the header is checked, not the bitstream.
 */
final class WebpHeader {

  private WebpHeader() {}

  /** Width and height, or empty when the header is truncated or inconsistent. */
  static Optional<int[]> dimensions(byte[] b) {
    if (b.length < 20) {
      return Optional.empty();
    }
    long riffSize = uint32(b, 4);
    // RIFF size counts everything after the first 8 bytes; trailing padding beyond it is tolerated.
    if (riffSize < 12 || riffSize + 8 > b.length) {
      return Optional.empty();
    }
    long chunkSize = uint32(b, 16);
    if (chunkSize + 20 > riffSize + 8) {
      return Optional.empty();
    }
    String chunk = new String(b, 12, 4, java.nio.charset.StandardCharsets.US_ASCII);
    return switch (chunk) {
      case "VP8 " -> lossy(b);
      case "VP8L" -> lossless(b);
      case "VP8X" -> extended(b);
      default -> Optional.empty();
    };
  }

  private static Optional<int[]> lossy(byte[] b) {
    // 3-byte frame tag, then the key frame start code 9D 01 2A and two 14-bit sizes.
    if (b.length < 30 || (b[23] & 0xFF) != 0x9D || (b[24] & 0xFF) != 0x01 || (b[25] & 0xFF) != 0x2A) {
      return Optional.empty();
    }
    if ((b[20] & 0x01) != 0) {
      return Optional.empty(); // not a key frame
    }
    return positive(uint16(b, 26) & 0x3FFF, uint16(b, 28) & 0x3FFF);
  }

  private static Optional<int[]> lossless(byte[] b) {
    if (b.length < 25 || (b[20] & 0xFF) != 0x2F) {
      return Optional.empty();
    }
    int b0 = b[21] & 0xFF;
    int b1 = b[22] & 0xFF;
    int b2 = b[23] & 0xFF;
    int b3 = b[24] & 0xFF;
    if ((b3 >> 5) != 0) {
      return Optional.empty(); // version must be 0
    }
    int width = 1 + (b0 | ((b1 & 0x3F) << 8));
    int height = 1 + ((b1 >> 6) | (b2 << 2) | ((b3 & 0x0F) << 10));
    return positive(width, height);
  }

  private static Optional<int[]> extended(byte[] b) {
    if (b.length < 30) {
      return Optional.empty();
    }
    return positive(1 + uint24(b, 24), 1 + uint24(b, 27));
  }

  private static Optional<int[]> positive(int width, int height) {
    return width > 0 && height > 0 ? Optional.of(new int[] {width, height}) : Optional.empty();
  }

  private static int uint16(byte[] b, int at) {
    return (b[at] & 0xFF) | (b[at + 1] & 0xFF) << 8;
  }

  private static int uint24(byte[] b, int at) {
    return uint16(b, at) | (b[at + 2] & 0xFF) << 16;
  }

  private static long uint32(byte[] b, int at) {
    return uint24(b, at) | (long) (b[at + 3] & 0xFF) << 24;
  }
}
