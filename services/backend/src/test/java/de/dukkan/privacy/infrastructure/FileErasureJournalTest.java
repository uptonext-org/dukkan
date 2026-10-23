package de.dukkan.privacy.infrastructure;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileErasureJournalTest {

  @TempDir Path dir;

  @Test
  void keepsEveryErasureAcrossInstancesAndSkipsACutOffLine() throws IOException {
    Path file = dir.resolve("nested/erasures.log");
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    FileErasureJournal journal = new FileErasureJournal(file.toString());
    assertThat(journal.erasedAccounts()).isEmpty();

    journal.record(first, Instant.parse("2026-11-02T10:00:00Z"));
    journal.record(second, Instant.parse("2026-11-03T10:00:00Z"));
    Files.writeString(file, "3f2a9c", UTF_8, StandardOpenOption.APPEND);

    assertThat(new FileErasureJournal(file.toString()).erasedAccounts()).containsExactly(first, second);
    assertThat(Files.readAllLines(file, UTF_8).getFirst()).isEqualTo("%s,2026-11-02T10:00:00Z", first);
  }

  @Test
  void isOffWithoutAPath() {
    FileErasureJournal journal = new FileErasureJournal(" ");
    journal.record(UUID.randomUUID(), Instant.EPOCH);
    assertThat(journal.enabled()).isFalse();
    assertThat(journal.erasedAccounts()).isEmpty();
  }
}
