package de.dukkan.media.infrastructure;

import de.dukkan.media.application.MediaKeys;
import de.dukkan.media.application.MediaObject;
import de.dukkan.media.application.MediaStorage;
import de.dukkan.media.domain.ImageFormat;
import de.dukkan.shared.config.DukkanProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

/**
 * Stores media as plain files below {@code dukkan.media.directory}, sharded by the first two hex
 * characters of the key ({@code ab/abcd….jpg}). Writes go to a temporary file in the same
 * directory and are then renamed atomically. Only suitable for one instance or a shared
 * (ReadWriteMany) volume; see {@link MediaStorage} for the S3 path.
 */
@Component
public class FileSystemMediaStorage implements MediaStorage {

  private static final Logger LOG = LoggerFactory.getLogger(FileSystemMediaStorage.class);

  private final Path root;

  public FileSystemMediaStorage(DukkanProperties properties) {
    this.root = java.util.Objects.requireNonNull(properties.media().directory()).toAbsolutePath().normalize();
    try {
      Files.createDirectories(root);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot create dukkan.media.directory " + root, e);
    }
    if (!Files.isWritable(root)) {
      throw new IllegalStateException("dukkan.media.directory " + root + " is not writable");
    }
    LOG.info("Media directory: {}", root);
  }

  @Override
  public boolean exists(String key) {
    return Files.isRegularFile(path(key));
  }

  @Override
  public void put(String key, byte[] bytes, String contentType) {
    Path target = path(key);
    if (Files.isRegularFile(target)) {
      return;
    }
    try {
      Files.createDirectories(target.getParent());
      Path temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
      try {
        Files.write(temp, bytes);
        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
      } catch (FileAlreadyExistsException e) {
        // A concurrent upload of the same bytes won; the content is identical.
      } finally {
        Files.deleteIfExists(temp);
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot store media " + key, e);
    }
  }

  @Override
  public Optional<MediaObject> open(String key) {
    Optional<ImageFormat> format = MediaKeys.format(key);
    Path file = path(key);
    if (format.isEmpty() || !Files.isRegularFile(file)) {
      return Optional.empty();
    }
    try {
      return Optional.of(new MediaObject(key, format.get().contentType(), Files.size(file), new FileSystemResource(file)));
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read media " + key, e);
    }
  }

  private Path path(String key) {
    if (!MediaKeys.isValid(key)) {
      throw new IllegalArgumentException("Invalid media key");
    }
    return root.resolve(key.substring(0, 2)).resolve(key);
  }
}
