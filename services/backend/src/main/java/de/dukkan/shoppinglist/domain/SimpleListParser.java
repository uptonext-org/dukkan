package de.dukkan.shoppinglist.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Reads a typed or dictated shopping list without a language model: "2 Gurken, 1 kg Tomaten und
 * eine Packung Milch" becomes three items. Splits on commas (not decimal commas: "1,5 kg"), line
 * breaks and "und"/"and", reads a
 * leading number (digits, Arabic-Indic digits, fractions or a number word) and unit, and searches for
 * the rest in the plural and a guessed singular form.
 */
public final class SimpleListParser {

  public static final int MAX_ITEMS = 30;

  private static final Pattern SEPARATORS = Pattern.compile("\\s*(?:(?<!\\d),|,(?!\\d)|[;\\n\\r•·،؛]|\\s(?:und|sowie|and|plus|و)\\s)\\s*", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern FILLER =
      Pattern.compile(
          "^(?:und\\s+|and\\s+|(?:ich\\s+)?(?:brauche|möchte|hätte\\s+gerne?|nehme|kaufe)\\s+|bitte\\s+|noch\\s+|auch\\s+|i\\s+(?:need|want|would\\s+like)\\s+|please\\s+|also\\s+|some\\s+|etwas\\s+|أريد\\s+|اريد\\s+)+",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern TRAILING = Pattern.compile("[\\s.!?]*(?:bitte|please|danke|thanks)?[\\s.!?]*$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final Pattern NUMBER = Pattern.compile("^(\\d+(?:[.,]\\d+)?(?:/\\d+)?|½|¼)\\s*(?:x\\s+|×\\s*)?", Pattern.CASE_INSENSITIVE);
  private static final Pattern OF = Pattern.compile("^(?:von|of|de)\\s+", Pattern.CASE_INSENSITIVE);
  private static final Map<String, BigDecimal> NUMBER_WORDS =
      Map.ofEntries(
          Map.entry("ein", BigDecimal.ONE),
          Map.entry("eine", BigDecimal.ONE),
          Map.entry("einen", BigDecimal.ONE),
          Map.entry("einmal", BigDecimal.ONE),
          Map.entry("zwei", BigDecimal.valueOf(2)),
          Map.entry("drei", BigDecimal.valueOf(3)),
          Map.entry("vier", BigDecimal.valueOf(4)),
          Map.entry("fünf", BigDecimal.valueOf(5)),
          Map.entry("sechs", BigDecimal.valueOf(6)),
          Map.entry("sieben", BigDecimal.valueOf(7)),
          Map.entry("acht", BigDecimal.valueOf(8)),
          Map.entry("neun", BigDecimal.valueOf(9)),
          Map.entry("zehn", BigDecimal.TEN),
          Map.entry("zwölf", BigDecimal.valueOf(12)),
          Map.entry("halbes", new BigDecimal("0.5")),
          Map.entry("halbe", new BigDecimal("0.5")),
          Map.entry("a", BigDecimal.ONE),
          Map.entry("an", BigDecimal.ONE),
          Map.entry("one", BigDecimal.ONE),
          Map.entry("two", BigDecimal.valueOf(2)),
          Map.entry("three", BigDecimal.valueOf(3)),
          Map.entry("four", BigDecimal.valueOf(4)),
          Map.entry("five", BigDecimal.valueOf(5)),
          Map.entry("six", BigDecimal.valueOf(6)),
          Map.entry("ten", BigDecimal.TEN),
          Map.entry("dozen", BigDecimal.valueOf(12)),
          Map.entry("half", new BigDecimal("0.5")));
  private static final Map<String, String> UNITS =
      Map.ofEntries(
          Map.entry("kg", "kg"),
          Map.entry("kilo", "kg"),
          Map.entry("kilogramm", "kg"),
          Map.entry("kilogram", "kg"),
          Map.entry("كيلو", "kg"),
          Map.entry("كغ", "kg"),
          Map.entry("g", "g"),
          Map.entry("gr", "g"),
          Map.entry("gramm", "g"),
          Map.entry("gram", "g"),
          Map.entry("grams", "g"),
          Map.entry("غرام", "g"),
          Map.entry("جرام", "g"),
          Map.entry("l", "l"),
          Map.entry("liter", "l"),
          Map.entry("litre", "l"),
          Map.entry("لتر", "l"),
          Map.entry("ml", "ml"),
          Map.entry("milliliter", "ml"),
          Map.entry("stück", "piece"),
          Map.entry("stk", "piece"),
          Map.entry("pieces", "piece"),
          Map.entry("piece", "piece"),
          Map.entry("حبة", "piece"),
          Map.entry("packung", "pack"),
          Map.entry("packungen", "pack"),
          Map.entry("packs", "pack"),
          Map.entry("pack", "pack"),
          Map.entry("päckchen", "pack"),
          Map.entry("flasche", "pack"),
          Map.entry("flaschen", "pack"),
          Map.entry("bottle", "pack"),
          Map.entry("bottles", "pack"),
          Map.entry("dose", "pack"),
          Map.entry("dosen", "pack"),
          Map.entry("can", "pack"),
          Map.entry("cans", "pack"),
          Map.entry("glas", "pack"),
          Map.entry("gläser", "pack"),
          Map.entry("becher", "pack"),
          Map.entry("bund", "pack"),
          Map.entry("tüte", "pack"),
          Map.entry("tüten", "pack"),
          Map.entry("netz", "pack"),
          Map.entry("kiste", "pack"),
          Map.entry("علبة", "pack"),
          Map.entry("كيس", "pack"));

  private SimpleListParser() {}

  public static List<ListItem> parse(@Nullable String text) {
    if (text == null || text.isBlank()) {
      return List.of();
    }
    List<ListItem> items = new ArrayList<>();
    for (String part : SEPARATORS.splitAsStream(arabicDigits(text.strip())).toList()) {
      ListItem item = item(part);
      if (item != null) {
        items.add(item);
        if (items.size() == MAX_ITEMS) {
          break;
        }
      }
    }
    return items;
  }

  private static @Nullable ListItem item(String part) {
    String rest = TRAILING.matcher(FILLER.matcher(part.strip()).replaceFirst("")).replaceFirst("").strip();
    if (rest.isEmpty()) {
      return null;
    }
    String original = rest;
    BigDecimal value = null;
    Matcher number = NUMBER.matcher(rest);
    if (number.find()) {
      value = number(number.group(1));
      rest = rest.substring(number.end()).strip();
    } else {
      String first = firstWord(rest);
      BigDecimal word = NUMBER_WORDS.get(first.toLowerCase(Locale.ROOT));
      if (word != null && rest.length() > first.length()) {
        value = word;
        rest = rest.substring(first.length()).strip();
      }
    }
    String unit = "piece";
    String first = firstWord(rest);
    String known = UNITS.get(first.toLowerCase(Locale.ROOT).replaceAll("\\.$", ""));
    if (known != null && rest.length() > first.length()) {
      unit = known;
      rest = OF.matcher(rest.substring(first.length()).strip()).replaceFirst("").strip();
    }
    if (rest.isEmpty() || rest.chars().noneMatch(Character::isLetter) || UNITS.containsKey(rest.toLowerCase(Locale.ROOT))) {
      return null;
    }
    Amount amount = value == null ? Amount.ONE : Amount.of(value, unit);
    return new ListItem(original, searchTerms(rest), amount == null ? Amount.ONE : amount);
  }

  /** The words as said, then guessed singulars ("Tomaten" → "Tomate", "Zwiebeln" → "Zwiebel"). */
  static List<String> searchTerms(String words) {
    Set<String> terms = new LinkedHashSet<>();
    terms.add(words);
    String lower = words.toLowerCase(Locale.ROOT);
    if (words.length() > 4) {
      if (lower.endsWith("en") || lower.endsWith("es")) {
        terms.add(words.substring(0, words.length() - 1));
        terms.add(words.substring(0, words.length() - 2));
      } else if (lower.endsWith("n") || lower.endsWith("s") || lower.endsWith("e") || lower.endsWith("r")) {
        terms.add(words.substring(0, words.length() - 1));
      }
    }
    return List.copyOf(terms);
  }

  private static String firstWord(String text) {
    int space = text.indexOf(' ');
    return space < 0 ? text : text.substring(0, space);
  }

  private static @Nullable BigDecimal number(String token) {
    return switch (token) {
      case "½" -> new BigDecimal("0.5");
      case "¼" -> new BigDecimal("0.25");
      default -> {
        String normalized = token.replace(',', '.');
        int slash = normalized.indexOf('/');
        if (slash < 0) {
          yield new BigDecimal(normalized);
        }
        BigDecimal denominator = new BigDecimal(normalized.substring(slash + 1));
        yield denominator.signum() == 0 ? null : new BigDecimal(normalized.substring(0, slash)).divide(denominator, 3, RoundingMode.HALF_UP);
      }
    };
  }

  private static String arabicDigits(String text) {
    StringBuilder out = new StringBuilder(text.length());
    text.codePoints().forEach(c -> out.appendCodePoint(c >= 0x0660 && c <= 0x0669 ? '0' + (c - 0x0660) : c >= 0x06F0 && c <= 0x06F9 ? '0' + (c - 0x06F0) : c));
    return out.toString();
  }
}
