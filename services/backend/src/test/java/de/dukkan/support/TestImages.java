package de.dukkan.support;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;

/** Image fixtures generated in memory (no binary files in the repository). */
public final class TestImages {

  /** A valid 1×1 lossless WebP (RIFF/VP8L), 34 bytes. */
  public static final byte[] WEBP_1X1 = Base64.getDecoder().decode("UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA==");

  private TestImages() {}

  public static byte[] jpeg(int width, int height, Color color) {
    return encode(image(width, height, color), "jpg");
  }

  public static byte[] png(int width, int height, Color color) {
    return encode(image(width, height, color), "png");
  }

  /** A PNG whose IHDR claims the given size but which has no image data (enough to test header limits). */
  public static byte[] pngHeaderOnly(int width, int height) {
    ByteBuffer ihdr = ByteBuffer.allocate(13).putInt(width).putInt(height).put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.writeBytes(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
    chunk(out, "IHDR", ihdr.array());
    chunk(out, "IEND", new byte[0]);
    return out.toByteArray();
  }

  /** The first {@code fraction} of the bytes. */
  public static byte[] truncated(byte[] bytes, double fraction) {
    return Arrays.copyOf(bytes, (int) (bytes.length * fraction));
  }

  public static void chunk(ByteArrayOutputStream out, String type, byte[] data) {
    byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
    out.writeBytes(ByteBuffer.allocate(4).putInt(data.length).array());
    out.writeBytes(typeBytes);
    out.writeBytes(data);
    CRC32 crc = new CRC32();
    crc.update(typeBytes);
    crc.update(data);
    out.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
  }

  private static BufferedImage image(int width, int height, Color color) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    g.setColor(color);
    g.fillRect(0, 0, width, height);
    g.setColor(Color.WHITE);
    g.drawLine(0, 0, width - 1, height - 1);
    g.dispose();
    return image;
  }

  private static byte[] encode(BufferedImage image, String format) {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      if (!ImageIO.write(image, format, out)) {
        throw new IllegalStateException("No ImageIO writer for " + format);
      }
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
