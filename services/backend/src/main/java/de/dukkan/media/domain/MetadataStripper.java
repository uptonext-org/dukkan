package de.dukkan.media.domain;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;

/**
 * Removes metadata from uploaded images without re-encoding them (B-32b). Phone photos carry EXIF
 * (GPS position, device, time), XMP and IPTC; uploads are served publicly, so none of that may
 * stay. The pixels are copied byte for byte. The one kept fact is the JPEG EXIF orientation, so a
 * portrait photo still shows upright: it is written back as a minimal EXIF block with only that
 * tag.
 *
 * <ul>
 *   <li>JPEG: drops APP1 (EXIF, XMP), APP3–APP13 and APP15 (vendor data, Photoshop/IPTC) and
 *       comments; keeps APP0 (JFIF), APP2 only for ICC profiles, APP14 (Adobe colour transform).
 *   <li>PNG: drops {@code eXIf}, {@code tEXt}, {@code zTXt}, {@code iTXt} and {@code tIME} chunks.
 *   <li>WebP: drops {@code EXIF} and {@code XMP } chunks, clears their VP8X flags and fixes the
 *       RIFF size.
 * </ul>
 *
 * Malformed structures throw {@link IllegalArgumentException}.
 */
public final class MetadataStripper {

  private static final Set<String> PNG_METADATA_CHUNKS = Set.of("eXIf", "tEXt", "zTXt", "iTXt", "tIME");
  private static final byte[] EXIF_HEADER = "Exif\0\0".getBytes(StandardCharsets.ISO_8859_1);
  private static final byte[] ICC_HEADER = "ICC_PROFILE\0".getBytes(StandardCharsets.ISO_8859_1);
  private static final int ORIENTATION_TAG = 0x0112;

  private MetadataStripper() {}

  public static byte[] strip(byte[] bytes, ImageFormat format) {
    return switch (format) {
      case JPEG -> jpeg(bytes);
      case PNG -> png(bytes);
      case WEBP -> webp(bytes);
    };
  }

  // ------------------------------------------------------------------ JPEG

  static byte[] jpeg(byte[] in) {
    if (in.length < 4 || u8(in, 0) != 0xFF || u8(in, 1) != 0xD8) {
      throw new IllegalArgumentException("not a JPEG");
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
    out.write(0xFF);
    out.write(0xD8);
    int orientation = 1;
    boolean orientationWritten = false;
    int pos = 2;
    while (true) {
      if (pos >= in.length || u8(in, pos) != 0xFF) {
        throw new IllegalArgumentException("JPEG marker expected at " + pos);
      }
      while (pos < in.length && u8(in, pos) == 0xFF) {
        pos++; // fill bytes
      }
      if (pos >= in.length) {
        throw new IllegalArgumentException("truncated JPEG");
      }
      int marker = u8(in, pos);
      pos++;
      if (marker == 0xD9) {
        out.write(0xFF);
        out.write(0xD9);
        return out.toByteArray();
      }
      if ((marker >= 0xD0 && marker <= 0xD7) || marker == 0x01) {
        out.write(0xFF);
        out.write(marker);
        continue;
      }
      if (pos + 2 > in.length) {
        throw new IllegalArgumentException("truncated JPEG segment");
      }
      int length = (u8(in, pos) << 8) | u8(in, pos + 1);
      if (length < 2 || pos + length > in.length) {
        throw new IllegalArgumentException("bad JPEG segment length");
      }
      int payload = pos + 2;
      int payloadLength = length - 2;
      if (marker == 0xE1 && startsWith(in, payload, payloadLength, EXIF_HEADER)) {
        orientation = orientation(in, payload + EXIF_HEADER.length, payloadLength - EXIF_HEADER.length);
      }
      boolean keep =
          marker < 0xE0 // frame, tables, SOS …: image data (COM, 0xFE, is above)
              || marker == 0xE0 // JFIF
              || marker == 0xEE // Adobe
              || (marker == 0xE2 && startsWith(in, payload, payloadLength, ICC_HEADER));
      if (!orientationWritten && marker != 0xE0 && orientation != 1) {
        writeOrientation(out, orientation);
        orientationWritten = true;
      }
      if (marker == 0xDA) {
        // Start of scan: entropy-coded data up to EOI is copied unchanged.
        if (!orientationWritten && orientation != 1) {
          writeOrientation(out, orientation);
        }
        out.write(in, pos - 2, in.length - (pos - 2));
        return out.toByteArray();
      }
      if (keep) {
        out.write(in, pos - 2, length + 2);
      }
      pos += length;
    }
  }

  /** The EXIF orientation (1–8) from a TIFF structure, 1 when absent or unreadable. */
  static int orientation(byte[] in, int start, int length) {
    if (length < 8) {
      return 1;
    }
    boolean bigEndian;
    if (in[start] == 'M' && in[start + 1] == 'M') {
      bigEndian = true;
    } else if (in[start] == 'I' && in[start + 1] == 'I') {
      bigEndian = false;
    } else {
      return 1;
    }
    long ifd = u32(in, start + 4, bigEndian);
    if (ifd < 8 || ifd + 2 > length) {
      return 1;
    }
    int entries = u16(in, start + (int) ifd, bigEndian);
    for (int i = 0; i < entries; i++) {
      int entry = start + (int) ifd + 2 + i * 12;
      if (entry + 12 > start + length) {
        return 1;
      }
      if (u16(in, entry, bigEndian) == ORIENTATION_TAG && u16(in, entry + 2, bigEndian) == 3) {
        int value = u16(in, entry + 8, bigEndian);
        return value >= 1 && value <= 8 ? value : 1;
      }
    }
    return 1;
  }

  /** APP1 with "Exif", a big-endian TIFF header and one IFD entry: Orientation (SHORT). */
  private static void writeOrientation(ByteArrayOutputStream out, int orientation) {
    byte[] segment = {
      (byte) 0xFF, (byte) 0xE1, 0, 34, // marker, length 34
      'E', 'x', 'i', 'f', 0, 0,
      'M', 'M', 0, 42, 0, 0, 0, 8, // TIFF header, IFD0 at 8
      0, 1, // one entry
      0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, (byte) orientation, 0, 0, // Orientation, SHORT, 1 value
      0, 0, 0, 0 // no next IFD
    };
    out.write(segment, 0, segment.length);
  }

  // ------------------------------------------------------------------ PNG

  static byte[] png(byte[] in) {
    if (in.length < 8) {
      throw new IllegalArgumentException("not a PNG");
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
    out.write(in, 0, 8);
    int pos = 8;
    while (pos < in.length) {
      if (pos + 12 > in.length) {
        throw new IllegalArgumentException("truncated PNG chunk");
      }
      long length = u32(in, pos, true);
      if (length > in.length - pos - 12L) {
        throw new IllegalArgumentException("bad PNG chunk length");
      }
      String type = new String(in, pos + 4, 4, StandardCharsets.ISO_8859_1);
      int total = (int) length + 12;
      if (!PNG_METADATA_CHUNKS.contains(type)) {
        out.write(in, pos, total);
      }
      pos += total;
      if (type.equals("IEND")) {
        break;
      }
    }
    return out.toByteArray();
  }

  // ------------------------------------------------------------------ WebP

  static byte[] webp(byte[] in) {
    if (in.length < 12 || !new String(in, 0, 4, StandardCharsets.ISO_8859_1).equals("RIFF")) {
      throw new IllegalArgumentException("not a WebP");
    }
    long riffEnd = Math.min(in.length, 8 + u32(in, 4, false));
    ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
    out.write(in, 0, 12); // RIFF, size (fixed below), WEBP
    int pos = 12;
    int vp8xFlagsAt = -1;
    while (pos + 8 <= riffEnd) {
      String fourCc = new String(in, pos, 4, StandardCharsets.ISO_8859_1);
      long size = u32(in, pos + 4, false);
      long padded = size + (size & 1);
      if (pos + 8 + size > riffEnd) {
        throw new IllegalArgumentException("bad WebP chunk size");
      }
      int total = (int) Math.min(8 + padded, riffEnd - pos);
      if (!fourCc.equals("EXIF") && !fourCc.equals("XMP ")) {
        if (fourCc.equals("VP8X") && size >= 1) {
          vp8xFlagsAt = out.size() + 8;
        }
        out.write(in, pos, total);
      }
      pos += total;
    }
    byte[] result = out.toByteArray();
    if (vp8xFlagsAt >= 0) {
      result[vp8xFlagsAt] = (byte) (result[vp8xFlagsAt] & ~0x0C); // EXIF (0x08) and XMP (0x04) flags
    }
    long riffSize = result.length - 8L;
    result[4] = (byte) riffSize;
    result[5] = (byte) (riffSize >>> 8);
    result[6] = (byte) (riffSize >>> 16);
    result[7] = (byte) (riffSize >>> 24);
    return result;
  }

  // ------------------------------------------------------------------ bytes

  private static boolean startsWith(byte[] in, int offset, int length, byte[] prefix) {
    return length >= prefix.length && Arrays.equals(in, offset, offset + prefix.length, prefix, 0, prefix.length);
  }

  private static int u8(byte[] in, int i) {
    return in[i] & 0xFF;
  }

  private static int u16(byte[] in, int i, boolean bigEndian) {
    return bigEndian ? (u8(in, i) << 8) | u8(in, i + 1) : (u8(in, i + 1) << 8) | u8(in, i);
  }

  private static long u32(byte[] in, int i, boolean bigEndian) {
    return bigEndian
        ? ((long) u8(in, i) << 24) | ((long) u8(in, i + 1) << 16) | ((long) u8(in, i + 2) << 8) | u8(in, i + 3)
        : ((long) u8(in, i + 3) << 24) | ((long) u8(in, i + 2) << 16) | ((long) u8(in, i + 1) << 8) | u8(in, i);
  }
}
