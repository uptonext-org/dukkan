package de.dukkan.media.api;

import de.dukkan.media.application.MediaObject;
import de.dukkan.media.application.MediaService;
import de.dukkan.media.domain.ImageResizer;
import de.dukkan.shared.error.InvalidRequestException;
import de.dukkan.shared.error.NotFoundException;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public image delivery. Keys are content-addressed, so a response never changes: one year,
 * immutable. The content type comes from the key (which came from the magic bytes), and {@code
 * nosniff} plus a sandboxing CSP keep browsers from interpreting the bytes as anything else.
 */
@RequestMapping(MediaUrls.PATH)
@RestController
final class MediaController {

  private static final CacheControl IMMUTABLE = CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();

  private final MediaService media;

  MediaController(MediaService media) {
    this.media = media;
  }

  /** {@code w}: one of 160, 320, 640, 1080 for a smaller copy (B-32d); without it, the original. */
  @GetMapping("/{key}")
  ResponseEntity<Resource> get(@PathVariable String key, @RequestParam(required = false) @Nullable Integer w) {
    if (w != null && !ImageResizer.WIDTHS.contains(w)) {
      throw new InvalidRequestException("w", "OneOf", "must be one of 160, 320, 640, 1080");
    }
    MediaObject object =
        (w == null ? media.open(key) : media.openVariant(key, w)).orElseThrow(() -> new NotFoundException("Media not found."));
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(object.contentType()))
        .contentLength(object.sizeBytes())
        .cacheControl(IMMUTABLE)
        .eTag(object.key().substring(0, object.key().indexOf('.')))
        .header("X-Content-Type-Options", "nosniff")
        .header("Content-Security-Policy", "default-src 'none'; sandbox")
        .header("Content-Disposition", "inline")
        .body(object.content());
  }
}
