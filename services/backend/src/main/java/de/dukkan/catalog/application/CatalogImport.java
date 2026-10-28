package de.dukkan.catalog.application;

import de.dukkan.catalog.application.ListingImport.RowError;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Bulk import into the shared catalog by platform admins (start catalog for production). */
public final class CatalogImport {

  private CatalogImport() {}

  /** A new shared product, one that is already in the shared catalog (skipped), or an error. */
  public enum Action {
    CREATE,
    EXISTS,
    ERROR
  }

  public record RowResult(int line, Action action, @Nullable String name, @Nullable UUID productId, List<RowError> errors) {}

  /**
   * The report. All or nothing: products are only written when no row has an error and it is not a
   * dry run ({@code applied}). Rows already in the shared catalog are skipped, so a file can be
   * imported again.
   */
  public record Result(boolean dryRun, boolean applied, int created, int existing, int failed, List<RowResult> rows) {}
}
