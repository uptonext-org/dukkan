package de.dukkan.media.domain;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

/**
 * Smaller copies of uploaded images for lists and tiles (B-32d). JPEG stays JPEG (quality 0.82),
 * PNG stays PNG (transparency). The JPEG EXIF orientation is applied to the pixels, since the copy
 * carries no metadata. Decoding subsamples the source to about twice the target width, so even a
 * 40-megapixel photo needs only a few megabytes of memory. WebP cannot be decoded by the JDK and
 * is served in its original size.
 */
public final class ImageResizer {

  /** The widths clients may ask for, in pixels. */
  public static final Set<Integer> WIDTHS = Set.of(160, 320, 640, 1080);

  private static final float JPEG_QUALITY = 0.82f;

  private ImageResizer() {}

  /** The resized image, or empty when the original is not wider than {@code width} or is WebP. */
  public static Optional<byte[]> resize(byte[] original, ImageFormat format, int width) {
    if (!WIDTHS.contains(width)) {
      throw new IllegalArgumentException("unsupported width " + width);
    }
    if (format == ImageFormat.WEBP) {
      return Optional.empty();
    }
    int orientation = format == ImageFormat.JPEG ? jpegOrientation(original) : 1;
    try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(original))) {
      Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
      if (!readers.hasNext()) {
        throw new IllegalArgumentException("no reader for " + format);
      }
      ImageReader reader = readers.next();
      try {
        reader.setInput(in, true, true);
        boolean sideways = orientation >= 5;
        int sourceWidth = sideways ? reader.getHeight(0) : reader.getWidth(0);
        if (sourceWidth <= width) {
          return Optional.empty();
        }
        ImageReadParam param = reader.getDefaultReadParam();
        int step = Math.max(1, sourceWidth / (width * 2));
        param.setSourceSubsampling(step, step, 0, 0);
        BufferedImage decoded = reader.read(0, param);
        BufferedImage upright = orient(decoded, orientation);
        BufferedImage scaled = scale(upright, width, format == ImageFormat.PNG);
        return Optional.of(encode(scaled, format));
      } finally {
        reader.dispose();
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot resize image", e);
    }
  }

  /** The EXIF orientation (1–8) of a JPEG, 1 when there is none. */
  static int jpegOrientation(byte[] jpeg) {
    int pos = 2;
    while (pos + 4 <= jpeg.length && (jpeg[pos] & 0xFF) == 0xFF) {
      int marker = jpeg[pos + 1] & 0xFF;
      if (marker == 0xDA || marker == 0xD9) {
        break;
      }
      int length = ((jpeg[pos + 2] & 0xFF) << 8) | (jpeg[pos + 3] & 0xFF);
      int payload = pos + 4;
      if (marker == 0xE1
          && length >= 8
          && payload + 6 <= jpeg.length
          && jpeg[payload] == 'E'
          && jpeg[payload + 1] == 'x'
          && jpeg[payload + 2] == 'i'
          && jpeg[payload + 3] == 'f') {
        return MetadataStripper.orientation(jpeg, payload + 6, Math.min(length - 8, jpeg.length - payload - 6));
      }
      pos += 2 + length;
    }
    return 1;
  }

  /** Applies an EXIF orientation (2–8) so that the pixels are upright. */
  static BufferedImage orient(BufferedImage image, int orientation) {
    if (orientation <= 1 || orientation > 8) {
      return image;
    }
    int w = image.getWidth();
    int h = image.getHeight();
    boolean sideways = orientation >= 5;
    AffineTransform t = new AffineTransform();
    switch (orientation) {
      case 2 -> {
        t.translate(w, 0);
        t.scale(-1, 1);
      }
      case 3 -> {
        t.translate(w, h);
        t.rotate(Math.PI);
      }
      case 4 -> {
        t.translate(0, h);
        t.scale(1, -1);
      }
      case 5 -> {
        t.rotate(Math.PI / 2);
        t.scale(1, -1);
      }
      case 6 -> {
        t.translate(h, 0);
        t.rotate(Math.PI / 2);
      }
      case 7 -> {
        t.scale(-1, 1);
        t.translate(-h, 0);
        t.translate(0, w);
        t.rotate(3 * Math.PI / 2);
      }
      default -> { // 8
        t.translate(0, w);
        t.rotate(3 * Math.PI / 2);
      }
    }
    BufferedImage out = new BufferedImage(sideways ? h : w, sideways ? w : h, image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
    Graphics2D g = out.createGraphics();
    try {
      g.drawImage(image, t, null);
    } finally {
      g.dispose();
    }
    return out;
  }

  private static BufferedImage scale(BufferedImage image, int width, boolean keepAlpha) {
    int height = Math.max(1, Math.round(image.getHeight() * (width / (float) image.getWidth())));
    BufferedImage out = new BufferedImage(width, height, keepAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
    Graphics2D g = out.createGraphics();
    try {
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.drawImage(image, 0, 0, width, height, null);
    } finally {
      g.dispose();
    }
    return out;
  }

  private static byte[] encode(BufferedImage image, ImageFormat format) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    if (format == ImageFormat.PNG) {
      ImageIO.write(image, "png", bytes);
      return bytes.toByteArray();
    }
    ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
    try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
      writer.setOutput(out);
      ImageWriteParam param = writer.getDefaultWriteParam();
      param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      param.setCompressionQuality(JPEG_QUALITY);
      writer.write(null, new IIOImage(image, null, null), param);
    } finally {
      writer.dispose();
    }
    return bytes.toByteArray();
  }
}
