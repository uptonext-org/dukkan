package de.dukkan.media.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.dukkan.support.TestImages;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** B-32d: smaller copies keep the aspect ratio, become upright and stay in their format. */
class ImageResizerTest {

  private static BufferedImage decode(byte[] bytes) throws IOException {
    return ImageIO.read(new ByteArrayInputStream(bytes));
  }

  private static boolean near(int rgb, Color expected) {
    Color c = new Color(rgb, true);
    return Math.abs(c.getRed() - expected.getRed()) < 40
        && Math.abs(c.getGreen() - expected.getGreen()) < 40
        && Math.abs(c.getBlue() - expected.getBlue()) < 40;
  }

  @Test
  void aWideJpegShrinksToTheWidthKeepingTheAspectRatio() throws IOException {
    byte[] photo = TestImages.jpeg(2000, 1000, new Color(0x3c6e47));
    byte[] small = ImageResizer.resize(photo, ImageFormat.JPEG, 320).orElseThrow();
    BufferedImage image = decode(small);
    assertThat(image.getWidth()).isEqualTo(320);
    assertThat(image.getHeight()).isEqualTo(160);
    assertThat(small.length).isLessThan(photo.length);
    assertThat(ImageInspector.inspect(small, 1_000_000).format()).isEqualTo(ImageFormat.JPEG);
  }

  /** Left half red, right half blue; after applying the orientation, where is red? */
  @ParameterizedTest
  @CsvSource({"1, left", "3, right", "6, top", "8, bottom"})
  void theExifOrientationIsAppliedToThePixels(int orientation, String redSide) throws IOException {
    byte[] photo = TestImages.withOrientation(TestImages.jpegHalves(1200, 800, Color.RED, Color.BLUE), orientation);
    BufferedImage image = decode(ImageResizer.resize(photo, ImageFormat.JPEG, 640).orElseThrow());
    boolean sideways = orientation >= 5;
    assertThat(image.getWidth()).isEqualTo(640);
    assertThat(image.getHeight()).isEqualTo(sideways ? 960 : 427);
    int w = image.getWidth();
    int h = image.getHeight();
    switch (redSide) {
      case "left" -> assertThat(near(image.getRGB(w / 4, h / 2), Color.RED) && near(image.getRGB(3 * w / 4, h / 2), Color.BLUE)).isTrue();
      case "right" -> assertThat(near(image.getRGB(3 * w / 4, h / 2), Color.RED) && near(image.getRGB(w / 4, h / 2), Color.BLUE)).isTrue();
      case "top" -> assertThat(near(image.getRGB(w / 2, h / 4), Color.RED) && near(image.getRGB(w / 2, 3 * h / 4), Color.BLUE)).isTrue();
      default -> assertThat(near(image.getRGB(w / 2, 3 * h / 4), Color.RED) && near(image.getRGB(w / 2, h / 4), Color.BLUE)).isTrue();
    }
  }

  @Test
  void pngKeepsTransparency() throws IOException {
    BufferedImage logo = new BufferedImage(800, 400, BufferedImage.TYPE_INT_ARGB);
    logo.setRGB(10, 10, 0x80FF0000);
    ByteArrayOutputStream png = new ByteArrayOutputStream();
    ImageIO.write(logo, "png", png);
    BufferedImage small = decode(ImageResizer.resize(png.toByteArray(), ImageFormat.PNG, 160).orElseThrow());
    assertThat(small.getWidth()).isEqualTo(160);
    assertThat(small.getColorModel().hasAlpha()).isTrue();
    assertThat(new Color(small.getRGB(150, 70), true).getAlpha()).isZero();
  }

  @Test
  void smallImagesWebpAndOddWidthsAreLeftAlone() {
    assertThat(ImageResizer.resize(TestImages.jpeg(300, 200, Color.GRAY), ImageFormat.JPEG, 320)).isEmpty();
    assertThat(ImageResizer.resize(TestImages.WEBP_1X1, ImageFormat.WEBP, 160)).isEmpty();
    assertThatThrownBy(() -> ImageResizer.resize(TestImages.jpeg(900, 600, Color.GRAY), ImageFormat.JPEG, 333)).isInstanceOf(IllegalArgumentException.class);
  }
}
