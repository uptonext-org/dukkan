package de.dukkan.shoppinglist.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Orders search hits for a shopping list item. The catalog search ranks names that start with the
 * term first, which suits typing ahead but not a list: "Tomaten" should find "Strauchtomaten" before
 * "Tomatenmark". In German compounds the last part names the thing, so a name word that equals the
 * term wins, then one that ends with it ("Salatgurke" for "Gurke"), then one that starts with it.
 */
public final class MatchRanking {

  private static final Pattern WORDS = Pattern.compile("[^\\p{L}\\p{N}]+");

  private MatchRanking() {}

  /** 0 (best) to 3; equal scores keep the search order. */
  public static int score(String term, String productName) {
    String t = term.toLowerCase(Locale.ROOT).strip();
    if (t.isEmpty()) {
      return 3;
    }
    int best = 3;
    for (String word : WORDS.splitAsStream(productName.toLowerCase(Locale.ROOT)).toList()) {
      if (word.equals(t)) {
        return 0;
      }
      if (word.length() > t.length() && word.endsWith(t)) {
        best = Math.min(best, 1);
      } else if (word.length() > t.length() && word.startsWith(t)) {
        best = Math.min(best, 2);
      }
    }
    return best;
  }
}
