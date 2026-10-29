package de.dukkan.merchant.application;

import de.dukkan.catalog.application.ListingImport.Row;
import de.dukkan.catalog.application.ListingImport.RowError;
import de.dukkan.shared.error.InvalidRequestException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Reads a listing import file (B-31): UTF-8 CSV as written by Excel, LibreOffice or Google Sheets.
 * The delimiter ({@code ;} or {@code ,} or tab) is detected from the header line; fields may be
 * quoted (RFC 4180). Columns are named in English or German, e.g. {@code price} or {@code preis},
 * names per language as {@code name_de}, {@code name_ar}. Lists ({@code keywords}, {@code
 * cuisines}, {@code allergens}) are separated by {@code |}.
 */
public final class ListingCsv {

  public static final int MAX_BYTES = 1_048_576;
  public static final int MAX_ROWS = 1000;

  private static final Pattern PRICE = Pattern.compile("^\\d{1,5}([.,]\\d{1,2})?$");
  private static final Pattern LIST_SEPARATOR = Pattern.compile("\\|");
  private static final Map<String, String> ALIASES =
      Map.ofEntries(
          Map.entry("ean", "barcode"),
          Map.entry("gtin", "barcode"),
          Map.entry("strichcode", "barcode"),
          Map.entry("preis", "price"),
          Map.entry("price_eur", "price"),
          Map.entry("preis_eur", "price"),
          Map.entry("groesse", "unit_size"),
          Map.entry("größe", "unit_size"),
          Map.entry("menge", "unit_size"),
          Map.entry("packungsgroesse", "unit_size"),
          Map.entry("packungsgröße", "unit_size"),
          Map.entry("kategorie", "category"),
          Map.entry("marke", "brand"),
          Map.entry("verfuegbar", "available"),
          Map.entry("verfügbar", "available"),
          Map.entry("max_menge", "max_order_quantity"),
          Map.entry("stichwoerter", "keywords"),
          Map.entry("stichwörter", "keywords"),
          Map.entry("kueche", "cuisines"),
          Map.entry("küche", "cuisines"),
          Map.entry("allergene", "allergens"),
          Map.entry("bio", "organic"));
  private static final List<String> COLUMNS =
      List.of("barcode", "brand", "unit_size", "category", "price", "available", "max_order_quantity", "keywords", "cuisines", "allergens", "organic");

  private ListingCsv() {}

  /** Parses the file; file-level problems (encoding, header, size) are a 400 for field {@code file}. */
  public static List<Row> parse(byte[] content, Collection<String> locales) {
    return parse(content, locales, true);
  }

  /**
   * A shared catalog file (admins): the same columns without prices; a {@code price} column is
   * ignored.
   */
  public static List<Row> parseCatalog(byte[] content, Collection<String> locales) {
    return parse(content, locales, false);
  }

  private static List<Row> parse(byte[] content, Collection<String> locales, boolean withPrices) {
    if (content.length > MAX_BYTES) {
      throw new InvalidRequestException("file", "Size", "the file may have at most 1 MB");
    }
    String text = decode(content);
    // Blank rows are skipped, but rows keep their number in the spreadsheet for error messages.
    List<List<String>> all = records(text, delimiter(text));
    List<List<String>> records = new ArrayList<>();
    List<Integer> spreadsheetRows = new ArrayList<>();
    for (int i = 0; i < all.size(); i++) {
      if (!all.get(i).stream().allMatch(String::isBlank)) {
        records.add(all.get(i));
        spreadsheetRows.add(i + 1);
      }
    }
    if (records.isEmpty()) {
      throw new InvalidRequestException("file", "Empty", "the file has no header line");
    }
    Map<String, Integer> header = header(records.getFirst(), locales);
    if (!withPrices) {
      header.remove("price");
      if (header.keySet().stream().noneMatch(c -> c.startsWith("name_"))) {
        throw new InvalidRequestException("file", "Header", "the header needs a name_<language> column, e.g. name_de");
      }
    } else if (!header.containsKey("price") || (!header.containsKey("barcode") && header.keySet().stream().noneMatch(c -> c.startsWith("name_")))) {
      throw new InvalidRequestException("file", "Header", "the header needs a price column and a name_<language> or barcode column");
    }
    if (records.size() - 1 > MAX_ROWS) {
      throw new InvalidRequestException("file", "TooManyRows", "at most " + MAX_ROWS + " products per file");
    }
    if (records.size() == 1) {
      throw new InvalidRequestException("file", "Empty", "the file has no product rows");
    }
    List<Row> rows = new ArrayList<>();
    for (int i = 1; i < records.size(); i++) {
      rows.add(row(spreadsheetRows.get(i), records.get(i), header, locales));
    }
    return rows;
  }

  private static String decode(byte[] content) {
    try {
      String text =
          StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(content)).toString();
      return text.startsWith("﻿") ? text.substring(1) : text;
    } catch (CharacterCodingException e) {
      throw new InvalidRequestException("file", "Encoding", "the file must be UTF-8 (in Excel: CSV UTF-8)");
    }
  }

  /** The most frequent candidate outside quotes in the first line. */
  static char delimiter(String text) {
    int semicolons = 0;
    int commas = 0;
    int tabs = 0;
    boolean quoted = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '"') {
        quoted = !quoted;
      } else if (!quoted && (c == '\n' || c == '\r')) {
        break;
      } else if (!quoted) {
        switch (c) {
          case ';' -> semicolons++;
          case ',' -> commas++;
          case '\t' -> tabs++;
          default -> {}
        }
      }
    }
    if (tabs > semicolons && tabs > commas) {
      return '\t';
    }
    return semicolons >= commas && semicolons > 0 ? ';' : ',';
  }

  /** RFC 4180 records: quoted fields may contain the delimiter, line breaks and {@code ""}. */
  static List<List<String>> records(String text, char delimiter) {
    List<List<String>> records = new ArrayList<>();
    List<String> record = new ArrayList<>();
    StringBuilder field = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (quoted) {
        if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
          field.append('"');
          i++;
        } else if (c == '"') {
          quoted = false;
        } else {
          field.append(c);
        }
      } else if (c == '"' && field.toString().isBlank()) {
        field.setLength(0);
        quoted = true;
      } else if (c == delimiter) {
        record.add(field.toString());
        field.setLength(0);
      } else if (c == '\n' || c == '\r') {
        if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
          i++;
        }
        record.add(field.toString());
        field.setLength(0);
        records.add(record);
        record = new ArrayList<>();
      } else {
        field.append(c);
      }
    }
    if (!field.isEmpty() || !record.isEmpty()) {
      record.add(field.toString());
      records.add(record);
    }
    return records;
  }

  private static Map<String, Integer> header(List<String> names, Collection<String> locales) {
    Map<String, Integer> header = new LinkedHashMap<>();
    for (int i = 0; i < names.size(); i++) {
      String raw = names.get(i).strip().toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
      String name = ALIASES.getOrDefault(raw, raw);
      if (name.startsWith("beschreibung_")) {
        name = "description_" + name.substring("beschreibung_".length());
      }
      boolean localized = (name.startsWith("name_") || name.startsWith("description_")) && locales.contains(name.substring(name.indexOf('_') + 1));
      if (!localized && !COLUMNS.contains(name)) {
        continue;
      }
      if (header.putIfAbsent(name, i) != null) {
        throw new InvalidRequestException("file", "Header", "the column " + name + " appears twice");
      }
    }
    return header;
  }

  private static Row row(int line, List<String> values, Map<String, Integer> header, Collection<String> locales) {
    List<RowError> errors = new ArrayList<>();
    Map<String, String> names = new LinkedHashMap<>();
    Map<String, String> descriptions = new LinkedHashMap<>();
    for (String locale : locales) {
      String name = value(values, header, "name_" + locale);
      if (name != null) {
        if (name.length() > 150) {
          errors.add(new RowError("name_" + locale, "Size", "at most 150 characters"));
        } else {
          names.put(locale, name);
        }
      }
      String description = value(values, header, "description_" + locale);
      if (description != null) {
        if (description.length() > 2000) {
          errors.add(new RowError("description_" + locale, "Size", "at most 2000 characters"));
        } else {
          descriptions.put(locale, description);
        }
      }
    }
    String barcode = value(values, header, "barcode");
    if (barcode != null) {
      barcode = barcode.replace(" ", "");
      if (!barcode.matches("^\\d{8,14}$")) {
        errors.add(new RowError("barcode", "Pattern", "8 to 14 digits (EAN/GTIN)"));
        barcode = null;
      }
    }
    String brand = limited(value(values, header, "brand"), 80, "brand", errors);
    String unitSize = limited(value(values, header, "unit_size"), 40, "unit_size", errors);
    String category = value(values, header, "category");
    Long price = price(value(values, header, "price"), errors);
    Boolean available = bool(value(values, header, "available"), "available", errors);
    Integer maxQuantity = null;
    String max = value(values, header, "max_order_quantity");
    if (max != null) {
      try {
        maxQuantity = Integer.valueOf(max);
        if (maxQuantity < 1 || maxQuantity > 99) {
          errors.add(new RowError("max_order_quantity", "Range", "1 to 99"));
          maxQuantity = null;
        }
      } catch (NumberFormatException e) {
        errors.add(new RowError("max_order_quantity", "Number", "a whole number from 1 to 99"));
      }
    }
    List<String> keywords = list(value(values, header, "keywords"), false);
    if (keywords.size() > 30 || keywords.stream().anyMatch(k -> k.length() > 60)) {
      errors.add(new RowError("keywords", "Size", "at most 30 keywords of 60 characters"));
      keywords = List.of();
    }
    List<String> cuisines = list(value(values, header, "cuisines"), true).stream().map(c -> c.toUpperCase(Locale.ROOT)).toList();
    List<String> allergens = list(value(values, header, "allergens"), true);
    if (allergens.size() > 20) {
      errors.add(new RowError("allergens", "Size", "at most 20 allergens"));
      allergens = List.of();
    }
    Boolean organic = bool(value(values, header, "organic"), "organic", errors);
    return new Row(
        line,
        barcode,
        names,
        descriptions,
        brand,
        unitSize,
        category == null ? null : category.toLowerCase(Locale.ROOT),
        price,
        available,
        maxQuantity,
        keywords,
        cuisines,
        allergens,
        Boolean.TRUE.equals(organic),
        List.copyOf(errors));
  }

  private static @Nullable String value(List<String> values, Map<String, Integer> header, String column) {
    Integer index = header.get(column);
    if (index == null || index >= values.size()) {
      return null;
    }
    String value = values.get(index).strip();
    return value.isEmpty() ? null : value;
  }

  private static @Nullable String limited(@Nullable String value, int max, String field, List<RowError> errors) {
    if (value != null && value.length() > max) {
      errors.add(new RowError(field, "Size", "at most " + max + " characters"));
      return null;
    }
    return value;
  }

  /** {@code 4,49}, {@code 4.49}, {@code 4,49 €} → 449 cents. */
  static @Nullable Long price(@Nullable String value, List<RowError> errors) {
    if (value == null) {
      return null;
    }
    String plain = value.replace("€", "").replace("EUR", "").replace(" ", "").replace(" ", "");
    if (!PRICE.matcher(plain).matches()) {
      errors.add(new RowError("price", "Pattern", "a price in euros like 4,49"));
      return null;
    }
    long cents = new BigDecimal(plain.replace(',', '.')).movePointRight(2).longValueExact();
    if (cents < 1) {
      errors.add(new RowError("price", "Positive", "the price must be more than 0"));
      return null;
    }
    return cents;
  }

  private static @Nullable Boolean bool(@Nullable String value, String field, List<RowError> errors) {
    if (value == null) {
      return null;
    }
    return switch (value.toLowerCase(Locale.ROOT)) {
      case "1", "true", "yes", "y", "ja", "j", "x", "wahr", "نعم" -> true;
      case "0", "false", "no", "n", "nein", "falsch", "لا" -> false;
      default -> {
        errors.add(new RowError(field, "Boolean", "ja or nein"));
        yield null;
      }
    };
  }

  private static List<String> list(@Nullable String value, boolean lowerCase) {
    if (value == null) {
      return List.of();
    }
    return LIST_SEPARATOR.splitAsStream(value).map(String::strip).filter(s -> !s.isEmpty()).map(s -> lowerCase ? s.toLowerCase(Locale.ROOT) : s).distinct().toList();
  }
}
