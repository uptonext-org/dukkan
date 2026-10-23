package de.dukkan.privacy.application;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Which accounts were erased, kept outside the database (a file on its own volume). A restored
 * backup contains accounts deleted after the backup was taken; {@link ErasureReplay} erases them
 * again at startup, so a restore never brings deleted personal data back (GDPR Art. 17).
 */
public interface ErasureJournal {

  /** Durably records the erasure before the deletion is reported as done. */
  void record(UUID userId, Instant erasedAt);

  /** Every account recorded so far. */
  Set<UUID> erasedAccounts();

  /** False when no journal is configured (development, tests without one). */
  boolean enabled();
}
