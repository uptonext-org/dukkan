package de.dukkan.media.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.dukkan.shared.error.ContentRejectedException;
import de.dukkan.support.TestImages;
import java.awt.Color;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ImageInspectorTest {

  private static final long MAX = 40_000_000;

  @Test
  void readsJpegPngAndWebpDimensions() {
    assertThat(ImageInspector.inspect(TestImages.jpeg(64, 48, Color.RED), MAX)).isEqualTo(new ImageInfo(ImageFormat.JPEG, 64, 48));
    assertThat(ImageInspector.inspect(TestImages.png(30, 20, Color.BLUE), MAX)).isEqualTo(new ImageInfo(ImageFormat.PNG, 30, 20));
    assertThat(ImageInspector.inspect(TestImages.WEBP_1X1, MAX)).isEqualTo(new ImageInfo(ImageFormat.WEBP, 1, 1));
  }

  @Test
  void readsLossyAndExtendedWebpHeaders() {
    // VP8 (lossy) key frame header: 640×480.
    byte[] lossy = riff("VP8 ", new byte[] {0x10, 0x02, 0x00, (byte) 0x9D, 0x01, 0x2A, (byte) 0x80, 0x02, (byte) 0xE0, 0x01});
    assertThat(ImageInspector.inspect(lossy, MAX)).isEqualTo(new ImageInfo(ImageFormat.WEBP, 640, 480));
    // VP8X canvas 8000×6000 (48 MP) → over the limit.
    byte[] extended = riff("VP8X", new byte[] {0, 0, 0, 0, 0x3F, 0x1F, 0x00, 0x6F, 0x17, 0x00});
    assertThat(ImageInspector.inspect(extended, 50_000_000)).isEqualTo(new ImageInfo(ImageFormat.WEBP, 8000, 6000));
    assertThatThrownBy(() -> ImageInspector.inspect(extended, MAX))
        .isInstanceOfSatisfying(ContentRejectedException.class, e -> assertThat(e.code()).isEqualTo("IMAGE_TOO_LARGE"));
  }

  @Test
  void rejectsOtherFormatsByContent() {
    for (byte[] bytes :
        new byte[][] {
          "GIF89a\u0001\u0000\u0001\u0000".getBytes(StandardCharsets.ISO_8859_1),
          "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8),
          "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8),
          "%PDF-1.7".getBytes(StandardCharsets.US_ASCII)
        }) {
      assertThatThrownBy(() -> ImageInspector.inspect(bytes, MAX))
          .isInstanceOfSatisfying(ContentRejectedException.class, e -> {
            assertThat(e.code()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
            assertThat(e.reason()).isEqualTo(ContentRejectedException.Reason.UNSUPPORTED_TYPE);
          });
    }
  }

  @Test
  void rejectsBytesThatDoNotDecode() {
    byte[] jpegMagicOnly = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01, 0x02, 0x03};
    byte[] truncatedJpeg = TestImages.truncated(TestImages.jpeg(200, 150, Color.GREEN), 0.5);
    byte[] truncatedPng = TestImages.truncated(TestImages.png(200, 150, Color.GREEN), 0.6);
    byte[] badWebp = TestImages.truncated(TestImages.WEBP_1X1, 0.7);
    for (byte[] bytes : new byte[][] {jpegMagicOnly, truncatedJpeg, truncatedPng, badWebp, TestImages.pngHeaderOnly(10, 10)}) {
      assertThatThrownBy(() -> ImageInspector.inspect(bytes, MAX))
          .isInstanceOfSatisfying(ContentRejectedException.class, e -> assertThat(e.code()).isEqualTo("INVALID_IMAGE"));
    }
  }

  @Test
  void checksThePixelLimitBeforeDecoding() {
    // Header says 8000×6000 = 48 MP; there is no pixel data, so a decode attempt would fail differently.
    assertThatThrownBy(() -> ImageInspector.inspect(TestImages.pngHeaderOnly(8000, 6000), MAX))
        .isInstanceOfSatisfying(ContentRejectedException.class, e -> assertThat(e.code()).isEqualTo("IMAGE_TOO_LARGE"));
  }

  /** A RIFF/WEBP container with one chunk. */
  private static byte[] riff(String fourCc, byte[] payload) {
    int riffSize = 4 + 8 + payload.length;
    byte[] b = new byte[8 + riffSize];
    System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, b, 0, 4);
    le32(b, 4, riffSize);
    System.arraycopy("WEBP".getBytes(StandardCharsets.US_ASCII), 0, b, 8, 4);
    System.arraycopy(fourCc.getBytes(StandardCharsets.US_ASCII), 0, b, 12, 4);
    le32(b, 16, payload.length);
    System.arraycopy(payload, 0, b, 20, payload.length);
    return b;
  }

  private static void le32(byte[] b, int at, int value) {
    b[at] = (byte) value;
    b[at + 1] = (byte) (value >> 8);
    b[at + 2] = (byte) (value >> 16);
    b[at + 3] = (byte) (value >> 24);
  }
}
