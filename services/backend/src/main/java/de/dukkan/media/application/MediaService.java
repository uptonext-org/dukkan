package de.dukkan.media.application;

import de.dukkan.media.domain.ImageInfo;
import de.dukkan.media.domain.ImageInspector;
import de.dukkan.media.domain.MetadataStripper;
import de.dukkan.shared.config.DukkanProperties;
import de.dukkan.shared.error.ContentRejectedException;
import java.util.Optional;
import java.util.UUID;
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
}
