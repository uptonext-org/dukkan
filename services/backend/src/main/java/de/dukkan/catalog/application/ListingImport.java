package de.dukkan.catalog.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Bulk import of listings (B-31): one row per product, from a merchant's CSV file. */
public final class ListingImport {

  private ListingImport() {}

  /** A problem with one row; {@code field} is the CSV column, e.g. {@code price}. */
  public record RowError(String field, String code, String message) {}

  /**
   * One parsed row. Values the parser could not read are {@code null} and come with an entry in
   * {@code errors}. {@code available} and {@code maxOrderQuantity} {@code null} keep the current
   * values of an existing listing (defaults for new ones).
   */
  public record Row(
      int line,
      @Nullable String barcode,
      Map<String, String> names,
      Map<String, String> descriptions,
      @Nullable String brand,
      @Nullable String unitSize,
      @Nullable String categorySlug,
      @Nullable Long priceCents,
      @Nullable Boolean available,
      @Nullable Integer maxOrderQuantity,
      List<String> keywords,
      List<String> cuisineCodes,
      List<String> allergens,
      boolean organic,
      List<RowError> errors) {}

  /** What happens to a row: a new private product, a listing of an existing product, or an update. */
  public enum Action {
    CREATE,
    LIST,
    UPDATE,
    ERROR
  }

  public record RowResult(int line, Action action, @Nullable String name, @Nullable UUID listingId, List<RowError> errors) {}

  /**
   * The report. All or nothing: rows are only written when no row has an error and it is not a
   * dry run ({@code applied}).
   */
  public record Result(boolean dryRun, boolean applied, int created, int listed, int updated, int failed, List<RowResult> rows) {}
}
