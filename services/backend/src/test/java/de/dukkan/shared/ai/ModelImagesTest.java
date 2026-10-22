package de.dukkan.shared.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ModelImagesTest {

  /** A 1-bit PNG: tiny on disk and in memory, but with as many pixels as asked for. */
  private static byte[] binaryPng(int width, int height) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY), "png", out);
    return out.toByteArray();
  }

  @Test
  void imagesWithTooManyPixelsAreNotDecoded() throws IOException {
    byte[] huge = binaryPng(7000, 6000);
    assertThat(huge.length).isLessThan(200_000);

    assertThat(ModelImages.prepare(new ModelImages.Image(huge, "image/png"))).isEmpty();
  }

  @Test
  void largeImagesWithinTheLimitAreScaledToTheModelSize() throws IOException {
    var prepared = ModelImages.prepare(new ModelImages.Image(binaryPng(6000, 4000), "image/png"));

    assertThat(prepared).hasValueSatisfying(image -> assertThat(image.contentType()).isEqualTo("image/jpeg"));
    BufferedImage decoded = ImageIO.read(new java.io.ByteArrayInputStream(prepared.orElseThrow().bytes()));
    assertThat(Math.max(decoded.getWidth(), decoded.getHeight())).isLessThanOrEqualTo(ModelImages.MAX_EDGE);
  }
}
