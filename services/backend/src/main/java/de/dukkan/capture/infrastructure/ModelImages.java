package de.dukkan.capture.infrastructure;

import de.dukkan.capture.application.ProductDraftExtractor.Image;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;
import javax.imageio.ImageIO;

/**
 * Prepares a stored photo for the model: images whose long edge is above {@link #MAX_EDGE} pixels
 * are scaled down and re-encoded as JPEG (the model scales larger images down anyway, so this only
 * saves upload time and tokens). The JDK cannot decode WebP, so a WebP photo is sent unchanged when
 * small enough and otherwise skipped.
 */
final class ModelImages {

  static final int MAX_EDGE = 1568;
  /** Base64 grows by a third; this keeps the encoded image below the API's 5 MB per image. */
  static final int MAX_BYTES = 3_700_000;

  private ModelImages() {}

  static Optional<Image> prepare(Image image) {
    if ("image/webp".equals(image.contentType())) {
      return image.bytes().length <= MAX_BYTES ? Optional.of(image) : Optional.empty();
    }
    BufferedImage decoded;
    try {
      decoded = ImageIO.read(new ByteArrayInputStream(image.bytes()));
    } catch (IOException e) {
      return Optional.empty();
    }
    if (decoded == null) {
      return Optional.empty();
    }
    int width = decoded.getWidth();
    int height = decoded.getHeight();
    int longEdge = Math.max(width, height);
    if (longEdge <= MAX_EDGE && image.bytes().length <= MAX_BYTES) {
      return Optional.of(image);
    }
    double scale = Math.min(1.0, (double) MAX_EDGE / longEdge);
    int targetWidth = Math.max(1, (int) Math.round(width * scale));
    int targetHeight = Math.max(1, (int) Math.round(height * scale));
    BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = scaled.createGraphics();
    try {
      graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      graphics.drawImage(decoded, 0, 0, targetWidth, targetHeight, java.awt.Color.WHITE, null);
    } finally {
      graphics.dispose();
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      if (!ImageIO.write(scaled, "jpg", out)) {
        return Optional.empty();
      }
    } catch (IOException e) {
      return Optional.empty();
    }
    byte[] bytes = out.toByteArray();
    return bytes.length <= MAX_BYTES ? Optional.of(new Image(bytes, "image/jpeg")) : Optional.empty();
  }
}
