package de.dukkan.media.domain;

import de.dukkan.shared.error.ContentRejectedException;
import java.awt.image.Raster;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

/**
 * Validates an uploaded image without trusting anything the client declared.
 *
 * <ol>
 *   <li>The format comes from the magic bytes (JPEG, PNG, WebP); anything else is unsupported.
 *   <li>Width and height come from the header, and the pixel limit is checked <em>before</em>
 *       any decoding (decompression bombs).
 *   <li>JPEG and PNG are then decoded completely with {@code javax.imageio}, subsampled to about
 *       one megapixel so that memory stays small; decoder errors and warnings (for example a
 *       truncated JPEG) reject the file. WebP is only header-checked: the JDK has no WebP decoder.
 * </ol>
 *
 * The bytes are never transformed or executed; the stored file is exactly the upload.
 */
public final class ImageInspector {

  private static final long DECODE_TARGET_PIXELS = 1_000_000;

  private ImageInspector() {}

  public static ImageInfo inspect(byte[] bytes, long maxPixels) {
    ImageFormat format =
        ImageFormat.sniff(bytes)
            .orElseThrow(() -> ContentRejectedException.unsupportedType("Only JPEG, PNG and WebP images are accepted."));
    ImageInfo info =
        switch (format) {
          case WEBP -> {
            int[] size =
                WebpHeader.dimensions(bytes)
                    .orElseThrow(() -> ContentRejectedException.invalidImage("The WebP header is invalid."));
            yield new ImageInfo(format, size[0], size[1]);
          }
          case JPEG, PNG -> decode(bytes, format, maxPixels);
        };
    checkPixels(info, maxPixels);
    return info;
  }

  private static void checkPixels(ImageInfo info, long maxPixels) {
    if (info.pixels() > maxPixels) {
      throw ContentRejectedException.imageTooLarge(
          "The image has " + info.pixels() + " pixels; at most " + maxPixels + " are allowed.");
    }
  }

  private static ImageInfo decode(byte[] bytes, ImageFormat format, long maxPixels) {
    Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format == ImageFormat.JPEG ? "jpeg" : "png");
    if (!readers.hasNext()) {
      throw new IllegalStateException("No ImageIO reader for " + format);
    }
    ImageReader reader = readers.next();
    try (ImageInputStream in = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
      reader.setInput(in, true, true);
      boolean[] warned = {false};
      reader.addIIOReadWarningListener((source, warning) -> warned[0] = true);
      ImageInfo info = new ImageInfo(format, reader.getWidth(0), reader.getHeight(0));
      if (info.width() < 1 || info.height() < 1) {
        throw ContentRejectedException.invalidImage("The image has no pixels.");
      }
      checkPixels(info, maxPixels);
      ImageReadParam param = reader.getDefaultReadParam();
      int step = (int) Math.max(1, Math.ceil(Math.sqrt((double) info.pixels() / DECODE_TARGET_PIXELS)));
      param.setSourceSubsampling(step, step, 0, 0);
      // readRaster skips colour conversion, so CMYK/YCCK JPEGs decode too; PNG has no raster path.
      Raster raster = reader.canReadRaster() ? reader.readRaster(0, param) : reader.read(0, param).getRaster();
      if (warned[0] || raster.getWidth() < 1 || raster.getHeight() < 1) {
        throw ContentRejectedException.invalidImage("The image data is incomplete or corrupt.");
      }
      return info;
    } catch (IOException | RuntimeException e) {
      if (e instanceof ContentRejectedException rejected) {
        throw rejected;
      }
      throw ContentRejectedException.invalidImage("The file could not be decoded as " + format + ".");
    } finally {
      reader.dispose();
    }
  }
}
