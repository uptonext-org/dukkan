package de.dukkan.capture.application;

import de.dukkan.capture.domain.ExtractedProduct;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Port to a language model that reads a product from text and/or a photo. */
public interface ProductDraftExtractor {

  /** False when no model is configured; {@link #extract} is then never called. */
  boolean enabled();

  /** Never throws; failures are reported as {@link Outcome.Failed}. */
  Outcome extract(Request request);

  /** A category the model may choose, with its localised names (locale → name). */
  record CategoryOption(String slug, Map<String, String> names) {}

  /** A photo as stored ({@code image/jpeg}, {@code image/png} or {@code image/webp}). */
  final class Image {
    private final byte[] bytes;
    private final String contentType;

    public Image(byte[] bytes, String contentType) {
      this.bytes = bytes.clone();
      this.contentType = contentType;
    }

    /** The encoded image; callers must not modify the array. */
    public byte[] bytes() {
      return bytes;
    }

    public String contentType() {
      return contentType;
    }
  }

  /** What to read; {@code locales} are the codes to fill names and descriptions in, primary first. */
  record Request(
      @Nullable String text,
      @Nullable Image image,
      List<String> locales,
      List<CategoryOption> categories,
      List<String> cuisineCodes,
      List<String> allergenCodes) {}

  /** Result of one extraction. */
  sealed interface Outcome {

    /** A draft; {@code imageAnalysed} is false when the photo was too large to send. */
    record Extracted(ExtractedProduct product, boolean imageAnalysed) implements Outcome {}

    /** The model declined (stop reason {@code refusal}). */
    record Declined() implements Outcome {}

    /** Transport error, timeout, truncated or unparsable answer. */
    record Failed() implements Outcome {}
  }
}
