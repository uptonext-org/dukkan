package de.dukkan.catalog.application;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Launch decision (docs/launch/go-live.md 3.1): no alcohol and no tobacco on Dukkan. Product names
 * that look like either get a hint: merchants see a warning on the listing, admins a mark in the
 * catalog review. Only a hint, never a block: "Malzbier" or "Rumkugeln" are allowed, and a
 * person decides.
 */
public final class RestrictedGoods {

  // Whole words, or the start of a word for stems of four letters and more, in German, English,
  // Turkish and Arabic.
  private static final List<String> ALCOHOL =
      List.of(
          "alkohol", "alcohol", "wein", "wine", "sekt", "prosecco", "champagner", "champagne", "schnaps", "likör", "likor",
          "liqueur", "wodka", "vodka", "whisky", "whiskey", "gin", "rum", "raki", "rakı", "ouzo", "arak", "arrak", "tequila",
          "cognac", "brandy", "weinbrand", "korn", "obstler", "grappa", "sake", "cider", "cidre", "pils", "ale",
          "bira", "şarap", "sarap", "votka", "viski",
          "بيرة", "بيره", "نبيذ", "خمر", "كحول", "عرق", "فودكا", "ويسكي");

  private static final List<String> TOBACCO =
      List.of(
          "tabak", "tobacco", "zigarette", "zigaretten", "zigarre", "zigarren", "zigarillo", "cigarette", "cigar",
          "e-zigarette", "nikotin", "nicotine", "snus", "shisha-tabak", "wasserpfeifentabak", "tütün", "tutun",
          "sigara", "puro", "nargile", "سجائر", "سيجارة", "تبغ", "دخان", "معسل", "نيكوتين", "شيشة");

  // Stems that also count inside a compound word ("Weißbier", "Rotwein", "Pfeifentabak").
  private static final List<String> ALCOHOL_COMPOUNDS = List.of("bier", "beer", "wein", "schnaps", "likör", "whisky", "wodka", "vodka");
  private static final List<String> TOBACCO_COMPOUNDS = List.of("tabak", "zigarett");

  // A drink declared alcohol-free is not alcohol ("Bier alkoholfrei", "Efes Malt alkolsüz").
  private static final Pattern ALCOHOL_FREE =
      Pattern.compile(
          "alkoholfrei|alcohol[- ]free|non[- ]alcoholic|0[.,]0 ?%|alkolsüz|alkolsuz|خالي من الكحول|بدون كحول",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  // Allowed although a stem matches.
  private static final Pattern ALLOWED =
      Pattern.compile(
          "malzbier|ingwerbier|ginger beer|ginger ale|root beer|rumkugel|weinbeere|weingummi|weinessig|wine vinegar"
              + "|weintraube|weinblätter|weinblatt|weinsauerkraut|bierschinken|bierwurst|şarap sirkesi|ginseng|gingembre|ginger|rumpsteak|kornflakes|korn(?:brot|mehl)|ginkgo|aleppo",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private static final Pattern WORD_SPLIT = Pattern.compile("[^\\p{L}\\p{N}-]+");

  private RestrictedGoods() {}

  /** True when one of the texts (names, brand) looks like alcohol or tobacco. */
  public static boolean suspected(@Nullable String... texts) {
    for (String text : texts) {
      if (text != null && suspected(text)) {
        return true;
      }
    }
    return false;
  }

  private static boolean suspected(String text) {
    String normalized = Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    String cleaned = ALLOWED.matcher(normalized).replaceAll(" ");
    List<String> words = WORD_SPLIT.splitAsStream(cleaned).filter(w -> !w.isEmpty()).toList();
    boolean alcoholFree = ALCOHOL_FREE.matcher(normalized).find();
    return (!alcoholFree && matches(words, ALCOHOL, ALCOHOL_COMPOUNDS)) || matches(words, TOBACCO, TOBACCO_COMPOUNDS);
  }

  private static boolean matches(List<String> words, List<String> stems, List<String> compounds) {
    return words.stream()
        .anyMatch(
            word ->
                stems.stream().anyMatch(stem -> word.equals(stem) || (word.startsWith(stem) && stem.length() >= 4))
                    || compounds.stream().anyMatch(word::contains));
  }
}
