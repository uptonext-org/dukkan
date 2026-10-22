package de.dukkan.media.application;

import de.dukkan.media.domain.ImageFormat;
import de.dukkan.media.domain.ImageInfo;
import de.dukkan.media.domain.ImageInspector;
import de.dukkan.media.domain.ImageResizer;
import de.dukkan.media.domain.MetadataStripper;
import de.dukkan.shared.config.DukkanProperties;
import de.dukkan.shared.error.ContentRejectedException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Accepts images and serves them back. Callers check permissions first (for merchants: market
 * ownership in {@code merchant.application.MerchantService}).
 */
@Service
public class MediaService {

  private static final Logger LOG = LoggerFactory.getLogger(MediaService.class);

  private final MediaStorage storage;
  private final UploadRateLimiter rateLimiter;
  private final long maxBytes;
  private final long maxPixels;
  /** Resizing decodes images: two at a time keeps CPU and memory bounded. */
  private final Semaphore resizing = new Semaphore(2);

  public MediaService(MediaStorage storage, UploadRateLimiter rateLimiter, DukkanProperties properties) {
    this.storage = storage;
    this.rateLimiter = rateLimiter;
    this.maxBytes = properties.media().maxFileSize().toBytes();
    this.maxPixels = properties.media().maxPixels();
  }

  /** Upper bound in bytes; callers should check the declared size before reading a stream. */
  public long maxBytes() {
    return maxBytes;
  }

  /** Counts the attempt against the user's upload window (429 when exhausted). */
  public void checkRateLimit(UUID userId) {
    rateLimiter.acquire(userId);
  }

  /** Validates and stores an image. Identical bytes yield the same key and are stored once. */
  public StoredMedia store(UUID uploaderId, MediaPurpose purpose, byte[] bytes) {
    if (bytes.length > maxBytes) {
      throw ContentRejectedException.tooLarge("The file is larger than " + maxBytes + " bytes.");
    }
    if (bytes.length == 0) {
      throw ContentRejectedException.invalidImage("The file is empty.");
    }
    ImageInfo info = ImageInspector.inspect(bytes, maxPixels);
    byte[] clean;
    try {
      // Uploads are public: EXIF (GPS, device), XMP and IPTC never reach storage (B-32b).
      clean = MetadataStripper.strip(bytes, info.format());
    } catch (IllegalArgumentException e) {
      throw ContentRejectedException.invalidImage("The image structure is damaged.");
    }
    String key = MediaKeys.of(clean, info.format());
    boolean existed = storage.exists(key);
    if (!existed) {
      storage.put(key, clean, info.format().contentType());
    }
    LOG.info(
        "Media {} {} {}x{} {} bytes by user {} ({})",
        purpose,
        key,
        info.width(),
        info.height(),
        clean.length,
        uploaderId,
        existed ? "deduplicated" : "stored");
    return new StoredMedia(key, info.format().contentType(), info.width(), info.height(), clean.length);
  }

  public Optional<MediaObject> open(String key) {
    return MediaKeys.isValid(key) ? storage.open(key) : Optional.empty();
  }

  /**
   * The image at most {@code width} pixels wide (B-32d): a stored variant, created on first use
   * (at most two at a time; when busy, the original is served). Originals that are not wider, and
   * WebP, are served as they are.
   */
  public Optional<MediaObject> openVariant(String key, int width) {
    Optional<ImageFormat> format = MediaKeys.format(key);
    if (!MediaKeys.isValid(key) || format.isEmpty()) {
      return Optional.empty();
    }
    if (format.get() == ImageFormat.WEBP) {
      return storage.open(key);
    }
    String variantKey = MediaKeys.variant(key, width, format.get());
    Optional<MediaObject> stored = storage.open(variantKey);
    if (stored.isPresent()) {
      return stored;
    }
    Optional<MediaObject> original = storage.open(key);
    if (original.isEmpty()) {
      return Optional.empty();
    }
    boolean acquired = false;
    try {
      acquired = resizing.tryAcquire(5, TimeUnit.SECONDS);
      if (!acquired) {
        return original;
      }
      byte[] bytes;
      try (InputStream in = original.get().content().getInputStream()) {
        bytes = in.readAllBytes();
      }
      Optional<byte[]> resized = ImageResizer.resize(bytes, format.get(), width);
      if (resized.isEmpty()) {
        return original;
      }
      storage.put(variantKey, resized.get(), format.get().contentType());
      return storage.open(variantKey).or(() -> original);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return original;
    } catch (IOException | RuntimeException e) {
      LOG.warn("Could not create the {} px variant of {}: {}", width, key, e.toString());
      return original;
    } finally {
      if (acquired) {
        resizing.release();
      }
    }
  }
}
