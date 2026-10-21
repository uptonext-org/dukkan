package de.dukkan.i18n.application;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Normalises text for language-independent search: lower case, Latin diacritics removed (ä→a,
 * ß→ss, ç→c, ł→l), Arabic tashkeel and tatweel removed, alef/hamza forms unified (أ إ آ ٱ → ا),
 * ى→ي, ة→ه, Arabic-Indic digits → 0-9, whitespace collapsed. Stored search columns and queries use
 * the same function.
 */
public final class TextNormalizer {

  /** Combining marks: Latin accents after NFD, Arabic harakat (U+064B–U+065F), superscript alef. */
  private static final Pattern MARKS = Pattern.compile("[\\p{M}\\u0670]");
  private static final Pattern SPACES = Pattern.compile("\\s+");

  private TextNormalizer() {}

  public static String normalize(@Nullable String input) {
    if (input == null || input.isBlank()) {
      return "";
    }
    String lower = input.strip().toLowerCase(Locale.ROOT).replace("ß", "ss");
    String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD);
    String withoutMarks = MARKS.matcher(decomposed).replaceAll("");
    StringBuilder out = new StringBuilder(withoutMarks.length());
    withoutMarks.codePoints().forEach(cp -> appendFolded(out, cp));
    return SPACES.matcher(Normalizer.normalize(out, Normalizer.Form.NFC)).replaceAll(" ").strip();
  }

  private static void appendFolded(StringBuilder out, int codePoint) {
    switch (codePoint) {
      case 'ł' -> out.append('l');
      case 'đ' -> out.append('d');
      case 'ø' -> out.append('o');
      case 'ı' -> out.append('i');
      case 'ـ' -> {} // tatweel
      case 'آ', 'أ', 'إ', 'ٱ' -> out.append('ا'); // آ أ إ ٱ → ا
      case 'ى', 'ی' -> out.append('ي'); // ى ی → ي
      case 'ة' -> out.append('ه'); // ة → ه
      case 'ک' -> out.append('ك'); // ک → ك
      default -> {
        if (codePoint >= '٠' && codePoint <= '٩') {
          out.append((char) ('0' + (codePoint - '٠')));
        } else {
          out.appendCodePoint(codePoint);
        }
      }
    }
  }

  /** {@code %term%} for ILIKE with LIKE wildcards in the term escaped (escape character {@code \}). */
  public static String containsPattern(String normalizedTerm) {
    return "%" + escapeLike(normalizedTerm) + "%";
  }

  /** {@code term%} for prefix matches. */
  public static String prefixPattern(String normalizedTerm) {
    return escapeLike(normalizedTerm) + "%";
  }

  private static String escapeLike(String term) {
    return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
