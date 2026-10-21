package de.dukkan.media.application;

import java.util.Optional;

/**
 * Port for the bytes of uploaded media. Keys are content-addressed ({@link MediaKeys}), so a
 * {@code put} of an existing key is a no-op and objects never change after they are written.
 *
 * <p>Implementations ({@code dukkan.media.storage}): {@code media.infrastructure.FileSystemMediaStorage}
 * ({@code filesystem}, default; one instance or a shared volume) and {@code S3MediaStorage}
 * ({@code s3}: S3, R2, MinIO, …; several instances). With a CDN in front, set {@code
 * dukkan.media.public-base-url} to the CDN and {@link #open} is no longer hit.
 */
public interface MediaStorage {

  boolean exists(String key);

  /** Stores the bytes under the key unless the key already exists. Must be atomic: readers never see partial files. */
  void put(String key, byte[] bytes, String contentType);

  Optional<MediaObject> open(String key);
}
