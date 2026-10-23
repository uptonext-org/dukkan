package de.dukkan.privacy.infrastructure;

import static java.nio.charset.StandardCharsets.UTF_8;

import de.dukkan.privacy.application.ErasureJournal;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Append-only text file, one {@code <account id>,<erased at>} line per erasure, flushed to disk
 * before the deletion request returns. It holds only random account ids (the anonymised rows keep
 * them too), no names or addresses. Configure {@code dukkan.privacy.erasure-journal} with a path on
 * a volume that a database restore does not touch.
 */
@Component
final class FileErasureJournal implements ErasureJournal {

  private static final Logger LOG = LoggerFactory.getLogger(FileErasureJournal.class);
  private static final Pattern COMMA = Pattern.compile(",");

  private final @Nullable Path file;

  FileErasureJournal(@Value("${dukkan.privacy.erasure-journal:}") @Nullable String path) {
    this.file = path == null || path.isBlank() ? null : Path.of(path.trim());
  }

  @Override
  public boolean enabled() {
    return file != null;
  }

  @Override
  public synchronized void record(UUID userId, Instant erasedAt) {
    if (file == null) {
      return;
    }
    byte[] line = (userId + "," + erasedAt + "\n").getBytes(UTF_8);
    try {
      Path parent = file.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
        channel.write(ByteBuffer.wrap(line));
        channel.force(true);
      }
    } catch (IOException e) {
      // Fails the deletion request (and rolls it back) rather than erasing without a record.
      throw new UncheckedIOException("Could not record the erasure in " + file, e);
    }
  }

  @Override
  public synchronized Set<UUID> erasedAccounts() {
    Set<UUID> ids = new LinkedHashSet<>();
    if (file == null || !Files.exists(file)) {
      return ids;
    }
    try {
      for (String line : Files.readAllLines(file, UTF_8)) {
        String id = COMMA.splitAsStream(line).findFirst().orElse("").trim();
        if (id.isEmpty()) {
          continue;
        }
        try {
          ids.add(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
          // A line cut off by a crash; the erasure it belonged to was not confirmed to the user.
          LOG.warn("Ignoring an unreadable line in the erasure journal");
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read the erasure journal " + file, e);
    }
    return ids;
  }
}
