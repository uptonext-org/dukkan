package de.dukkan.media.domain;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.dukkan.support.TestImages;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** B-32b: metadata leaves uploads, the image data stays byte for byte. */
class MetadataStripperTest {

  private static final String SECRET = "GPS 53.5511N 9.9937E Pixel-9-Pro";

  /** A little-endian EXIF block with Orientation and a text tag that carries {@link #SECRET}. */
  private static byte[] exifApp1(int orientation) {
    byte[] text = (SECRET + "\0").getBytes(ISO_8859_1);
    ByteBuffer tiff = ByteBuffer.allocate(8 + 2 + 2 * 12 + 4 + text.length).order(ByteOrder.LITTLE_ENDIAN);
    tiff.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8);
    tiff.putShort((short) 2);
    // Make (0x010F), ASCII, offset after the IFD.
    tiff.putShort((short) 0x010F).putShort((short) 2).putInt(text.length).putInt(8 + 2 + 24 + 4);
    tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0);
    tiff.putInt(0);
    tiff.put(text);
    return segment(0xE1, concat("Exif\0\0".getBytes(ISO_8859_1), tiff.array()));
  }

  private static byte[] segment(int marker, byte[] payload) {
    ByteBuffer out = ByteBuffer.allocate(4 + payload.length);
    out.put((byte) 0xFF).put((byte) marker).putShort((short) (payload.length + 2)).put(payload);
    return out.array();
  }

  private static byte[] concat(byte[]... parts) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (byte[] part : parts) {
      out.writeBytes(part);
    }
    return out.toByteArray();
  }

  private static boolean contains(byte[] haystack, String needle) {
    return new String(haystack, ISO_8859_1).contains(needle);
  }

  private static BufferedImage decode(byte[] bytes) throws IOException {
    return ImageIO.read(new ByteArrayInputStream(bytes));
  }

  @Test
  void jpegLosesExifXmpAndCommentsButKeepsOrientationAndPixels() throws IOException {
    byte[] plain = TestImages.jpeg(40, 20, new Color(0x7b2d26));
    byte[] xmp = segment(0xE1, ("http://ns.adobe.com/xap/1.0/\0<x:xmpmeta>" + SECRET + "</x:xmpmeta>").getBytes(ISO_8859_1));
    byte[] comment = segment(0xFE, ("Shot on " + SECRET).getBytes(ISO_8859_1));
    byte[] iptc = segment(0xED, ("Photoshop 3.0\0" + SECRET).getBytes(ISO_8859_1));
    // SOI, EXIF, XMP, comment, IPTC, then the rest of the encoder's file (APP0, tables, scan, EOI).
    byte[] photo = concat(Arrays.copyOfRange(plain, 0, 2), exifApp1(6), xmp, comment, iptc, Arrays.copyOfRange(plain, 2, plain.length));
    assertThat(contains(photo, SECRET)).isTrue();

    byte[] clean = MetadataStripper.strip(photo, ImageFormat.JPEG);

    assertThat(contains(clean, SECRET)).isFalse();
    assertThat(contains(clean, "xmpmeta")).isFalse();
    assertThat(clean.length).isLessThan(photo.length);
    int exif = new String(clean, ISO_8859_1).indexOf("Exif\0\0");
    assertThat(MetadataStripper.orientation(clean, exif + 6, clean.length - exif - 6)).isEqualTo(6);
    // The scan data is unchanged, so the decoded pixels are identical.
    BufferedImage before = decode(plain);
    BufferedImage after = decode(clean);
    assertThat(after.getWidth()).isEqualTo(40);
    assertThat(after.getRGB(5, 5)).isEqualTo(before.getRGB(5, 5));
    assertThat(ImageInspector.inspect(clean, 1_000_000).format()).isEqualTo(ImageFormat.JPEG);
  }

  @Test
  void anUprightJpegGetsNoExifAtAllAndStrippingIsStable() {
    byte[] plain = TestImages.jpeg(16, 16, Color.ORANGE);
    byte[] photo = concat(Arrays.copyOfRange(plain, 0, 2), exifApp1(1), Arrays.copyOfRange(plain, 2, plain.length));
    byte[] clean = MetadataStripper.strip(photo, ImageFormat.JPEG);
    assertThat(contains(clean, "Exif")).isFalse();
    assertThat(clean).isEqualTo(MetadataStripper.strip(plain, ImageFormat.JPEG));
    // Idempotent: the same upload gets the same content-addressed key.
    assertThat(MetadataStripper.strip(clean, ImageFormat.JPEG)).isEqualTo(clean);
  }

  @Test
  void pngLosesTextExifAndTimeChunks() throws IOException {
    byte[] plain = TestImages.png(12, 8, Color.BLUE);
    int iend = plain.length - 12;
    ByteArrayOutputStream extra = new ByteArrayOutputStream();
    TestImages.chunk(extra, "tEXt", ("Comment\0" + SECRET).getBytes(ISO_8859_1));
    TestImages.chunk(extra, "eXIf", ("MM\0*" + SECRET).getBytes(ISO_8859_1));
    TestImages.chunk(extra, "tIME", new byte[] {7, (byte) 0xEA, 10, 21, 12, 0, 0});
    byte[] photo = concat(Arrays.copyOfRange(plain, 0, iend), extra.toByteArray(), Arrays.copyOfRange(plain, iend, plain.length));

    byte[] clean = MetadataStripper.strip(photo, ImageFormat.PNG);

    assertThat(contains(clean, SECRET)).isFalse();
    assertThat(clean).isEqualTo(plain);
    assertThat(decode(clean).getRGB(3, 3)).isEqualTo(Color.BLUE.getRGB());
  }

  @Test
  void webpLosesExifAndXmpChunksAndTheirFlags() {
    // Extended WebP: VP8X (EXIF + XMP flags), the 1×1 lossless image, EXIF (odd size, padded), XMP.
    byte[] vp8l = Arrays.copyOfRange(TestImages.WEBP_1X1, 12, TestImages.WEBP_1X1.length);
    byte[] vp8x = riffChunk("VP8X", new byte[] {0x0C, 0, 0, 0, 0, 0, 0, 0, 0, 0});
    byte[] exif = riffChunk("EXIF", ("MM\0*" + SECRET + "!").getBytes(ISO_8859_1));
    byte[] xmp = riffChunk("XMP ", ("<x:xmpmeta>" + SECRET + "</x:xmpmeta>").getBytes(ISO_8859_1));
    byte[] body = concat("WEBP".getBytes(ISO_8859_1), vp8x, vp8l, exif, xmp);
    byte[] photo = concat("RIFF".getBytes(ISO_8859_1), le32(body.length), body);

    byte[] clean = MetadataStripper.strip(photo, ImageFormat.WEBP);

    assertThat(contains(clean, SECRET)).isFalse();
    assertThat(ByteBuffer.wrap(clean, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt()).isEqualTo(clean.length - 8);
    assertThat(clean[20] & 0x0C).isZero();
    assertThat(clean.length).isEqualTo(12 + vp8x.length + vp8l.length);
    assertThat(ImageInspector.inspect(clean, 1_000_000).format()).isEqualTo(ImageFormat.WEBP);
    // The simple form (no VP8X) passes unchanged.
    assertThat(MetadataStripper.strip(TestImages.WEBP_1X1, ImageFormat.WEBP)).isEqualTo(TestImages.WEBP_1X1);
  }

  @Test
  void damagedStructuresAreRejected() {
    assertThatThrownBy(() -> MetadataStripper.strip(new byte[] {(byte) 0xFF, (byte) 0xD8, 0x12}, ImageFormat.JPEG))
        .isInstanceOf(IllegalArgumentException.class);
    byte[] badPng = Arrays.copyOf(TestImages.png(4, 4, Color.RED), 20);
    assertThatThrownBy(() -> MetadataStripper.strip(badPng, ImageFormat.PNG)).isInstanceOf(IllegalArgumentException.class);
  }

  private static byte[] riffChunk(String fourCc, byte[] data) {
    byte[] padded = data.length % 2 == 1 ? Arrays.copyOf(data, data.length + 1) : data;
    return concat(fourCc.getBytes(ISO_8859_1), le32(data.length), padded);
  }

  private static byte[] le32(int value) {
    return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array();
  }
}
