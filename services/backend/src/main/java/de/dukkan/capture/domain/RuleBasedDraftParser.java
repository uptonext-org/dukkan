package de.dukkan.capture.domain;

import de.dukkan.i18n.application.TextNormalizer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Deterministic fallback when no language model is available: reads price, pack size, "organic",
 * and a category, allergens and cuisine from keywords in German, English, Arabic and Turkish. The
 * rest of the text becomes the name in one locale. It never translates; the shared catalog
 * matches carry the other languages.
 *
 * <p>Examples: {@code "Sucuk Gazi 400 g 4,49 €"} → name "Sucuk Gazi", 400 g = 0.4 kg, 449 cents,
 * category {@code meat}; {@code "عدس أحمر ١ كيلو ٢٫٩٩"} → name "عدس أحمر", 1 kg, 299 cents,
 * category {@code grains}.
 */
public final class RuleBasedDraftParser {

  private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;
  private static final String NUMBER = "(\\d{1,5}(?:[.,]\\d{1,3})?)";
  private static final String UNIT =
      "(kg|kilo|kilogramm|kilogram|g|gr|gramm|gram|grams|l|ltr|liter|litre|ml|cl|stk|stück|stueck|st|pcs|pc|piece|pieces"
          + "|adet|كيلو|كغ|كجم|كلغ|غرام|جرام|غ|جم|لتر|مل|حبة|حبات|قطعة|قطع)\\.?(?!\\p{L})";
  private static final Pattern MULTIPACK = Pattern.compile("(?<![\\d.,])(\\d{1,3})\\s*[x×]\\s*" + NUMBER + "\\s*" + UNIT, FLAGS);
  private static final Pattern SIZE = Pattern.compile("(?<![\\d.,])" + NUMBER + "\\s*" + UNIT, FLAGS);
  private static final Pattern PRICE_AFTER =
      Pattern.compile("(?<![\\d.,])(\\d{1,5}(?:[.,]\\d{1,2})?)\\s*(?:€|eur(?:o)?(?!\\p{L})|يورو)", FLAGS);
  private static final Pattern PRICE_BEFORE = Pattern.compile("(?:€|\\beur(?:o)?\\b)\\s*(\\d{1,5}(?:[.,]\\d{1,2})?)", FLAGS);
  private static final Pattern BARE_PRICE = Pattern.compile("(?<![\\d.,])(\\d{1,4}[.,]\\d{2})(?![\\d.,])");
  private static final Pattern ORGANIC = Pattern.compile("(?<!\\p{L})(bio|organic|öko|oeko|organik)(?!\\p{L})|عضوي", FLAGS);
  private static final Pattern ARABIC = Pattern.compile("\\p{IsArabic}");
  private static final Pattern NON_LETTERS = Pattern.compile("[^\\p{L}\\p{N}]+");
  private static final Pattern SPACES = Pattern.compile("\\s+");
  private static final Pattern EDGE_PUNCTUATION = Pattern.compile("^[\\s,;:/|·–—-]+|[\\s,;:/|·–—-]+$");

  /** Category slug → keywords (any language); the longest matched keywords win. */
  private static final Map<String, List<String>> CATEGORIES =
      normalizedTable(
          Map.ofEntries(
              Map.entry(
                  "fruit-veg",
                  List.of(
                      "obst", "gemüse", "apfel", "äpfel", "banane", "tomate", "gurke", "zwiebel", "kartoffel", "paprika",
                      "aubergine", "zucchini", "salat", "petersilie", "minze", "zitrone", "orange", "mango", "granatapfel",
                      "fruit", "vegetable", "apple", "banana", "tomato", "cucumber", "onion", "potato", "lemon", "parsley",
                      "مانجو", "فواكه", "خضار", "تفاح", "موز", "طماطم", "بندورة", "خيار", "بصل", "بطاطا", "باذنجان", "ليمون",
                      "بقدونس", "نعناع", "رمان", "elma", "domates", "salatalık", "soğan", "patates", "patlıcan", "limon",
                      "maydanoz", "nane", "biber")),
              Map.entry(
                  "bakery",
                  List.of(
                      "brot", "brötchen", "fladenbrot", "baguette", "simit", "pide", "lavash", "toast", "croissant",
                      "bread", "pita", "flatbread", "bun", "خبز", "صمون", "كعك", "ekmek", "pogaca", "poğaça", "chleb")),
              Map.entry(
                  "meat",
                  List.of(
                      "fleisch", "hähnchen", "huhn", "hühnchen", "rind", "rinder", "lamm", "kalb", "hack", "hackfleisch", "sucuk",
                      "pastirma", "pastırma", "wurst", "steak", "geflügel", "pute", "meat", "chicken", "beef", "lamb", "mince",
                      "sausage", "veal", "turkey", "لحم", "لحمة", "دجاج", "فروج", "خروف", "غنم", "عجل", "كفتة", "سجق", "مقانق",
                      "et", "tavuk", "kuzu", "dana", "kıyma", "kiełbasa", "kielbasa")),
              Map.entry(
                  "dairy",
                  List.of(
                      "milch", "käse", "joghurt", "jogurt", "ayran", "butter", "eier", "sahne", "quark", "feta", "halloumi",
                      "hellim", "labneh", "labne", "schafskäse", "milk", "cheese", "yogurt", "yoghurt", "eggs", "cream",
                      "حليب", "جبن", "جبنة", "لبن", "لبنة", "بيض", "زبدة", "قشطة", "süt", "peynir", "yoğurt", "yumurta",
                      "kaymak", "tereyağı", "twaróg", "twarog", "mleko", "ser")),
              Map.entry(
                  "grains",
                  List.of(
                      "reis", "linsen", "bulgur", "kichererbsen", "bohnen", "mehl", "couscous", "grieß", "hafer", "rice",
                      "lentils", "chickpeas", "beans", "flour", "oats", "semolina", "أرز", "رز", "عدس", "حمص", "برغل", "فريكة",
                      "طحين", "فول", "سميد", "pirinç", "mercimek", "nohut", "fasulye", "un", "kasza", "basmati")),
              Map.entry("pasta", List.of("nudeln", "spaghetti", "pasta", "noodles", "makkaroni", "معكرونة", "شعيرية", "makarna", "erişte", "makaron")),
              Map.entry(
                  "spices",
                  List.of(
                      "gewürz", "gewürze", "salz", "pfeffer", "öl", "olivenöl", "sauce", "soße", "paprikapulver", "pulver",
                      "kreuzkümmel", "kümmel", "sumach", "zaatar", "harissa", "chili", "curry", "kurkuma", "spice", "salt",
                      "pepper", "oil", "olive oil", "cumin", "turmeric", "بهارات", "بهار", "ملح", "زيت", "زعتر", "سماق", "كمون",
                      "كركم", "فلفل", "دبس", "baharat", "tuz", "yağ", "salça", "pul biber", "sumak", "kimyon", "garam", "masala")),
              Map.entry(
                  "deli",
                  List.of(
                      "oliven", "konserve", "tahin", "tahini", "hummus", "eingelegt", "pickles", "olives", "canned", "زيتون",
                      "مخلل", "طحينة", "معلبات", "zeytin", "turşu", "tahin", "ogórki")),
              Map.entry("frozen", List.of("tiefkühl", "tk", "gefroren", "frozen", "مجمد", "مثلج", "dondurulmuş", "mrożone")),
              Map.entry(
                  "sweets",
                  List.of(
                      "schokolade", "kekse", "baklava", "chips", "süßigkeiten", "halva", "helva", "lokum", "datteln",
                      "chocolate", "cookies", "biscuits", "sweets", "snack", "dates", "حلويات", "حلوى", "شوكولاتة", "بقلاوة",
                      "حلاوة", "معمول", "تمر", "بسكويت", "çikolata", "bisküvi", "tatlı", "hurma", "lokum", "czekolada")),
              Map.entry(
                  "drinks",
                  List.of(
                      "wasser", "saft", "cola", "limonade", "getränk", "sprudel", "water", "juice", "drink", "soda", "ماء",
                      "مياه", "عصير", "مشروب", "su", "meyve suyu", "gazoz", "şalgam", "sok", "woda")),
              Map.entry("tea-coffee", List.of("tee", "kaffee", "mokka", "espresso", "tea", "coffee", "شاي", "قهوة", "çay", "kahve", "herbata", "kawa"))));

  /** Fixed order so that ties go to the earlier category. */
  private static final List<String> CATEGORY_ORDER =
      List.of("fruit-veg", "bakery", "meat", "dairy", "grains", "pasta", "spices", "deli", "frozen", "sweets", "drinks", "tea-coffee");

  private static final Map<String, List<String>> ALLERGENS =
      normalizedTable(
          Map.of(
              "milk",
              List.of("milch", "käse", "joghurt", "ayran", "butter", "sahne", "quark", "feta", "halloumi", "hellim", "labneh", "milk",
                  "cheese", "yogurt", "yoghurt", "cream", "حليب", "جبن", "جبنة", "لبن", "لبنة", "زبدة", "قشطة", "süt", "peynir",
                  "yoğurt", "kaymak", "mleko", "twaróg"),
              "gluten",
              List.of("brot", "brötchen", "weizen", "mehl", "nudeln", "bulgur", "couscous", "grieß", "simit", "pide", "baklava", "kekse",
                  "bread", "wheat", "flour", "pasta", "noodles", "خبز", "طحين", "برغل", "سميد", "معكرونة", "بقلاوة", "ekmek",
                  "makarna", "un", "chleb"),
              "eggs", List.of("eier", "eggs", "بيض", "yumurta", "jajka"),
              "sesame", List.of("sesam", "tahin", "tahini", "simit", "halva", "helva", "sesame", "سمسم", "طحينة", "حلاوة", "susam"),
              "nuts",
              List.of("nuss", "nüsse", "mandel", "mandeln", "pistazie", "pistazien", "walnuss", "haselnuss", "cashew", "nut", "nuts",
                  "almond", "pistachio", "walnut", "hazelnut", "جوز", "لوز", "فستق", "بندق", "ceviz", "fıstık", "fındık", "badem"),
              "peanuts", List.of("erdnuss", "erdnüsse", "peanut", "peanuts", "فول سوداني", "yer fıstığı"),
              "fish", List.of("fisch", "lachs", "thunfisch", "sardine", "sardellen", "hering", "fish", "salmon", "tuna", "سمك", "سردين", "تونة", "balık", "ryba"),
              "soy", List.of("soja", "tofu", "soy", "صويا", "soya")));

  private static final Map<String, List<String>> CUISINES =
      normalizedTable(
          Map.of(
              "TURKISH", List.of("türkisch", "turkish", "تركي", "türk"),
              "ARABIC", List.of("arabisch", "arabic", "عربي", "libanesisch", "syrisch", "syrian", "lebanese", "سوري", "لبناني"),
              "INDIAN", List.of("indisch", "indian", "هندي"),
              "ASIAN", List.of("asiatisch", "asian", "آسيوي", "thai", "chinesisch", "japanisch", "korean", "vietnamesisch"),
              "POLISH", List.of("polnisch", "polish", "بولندي", "polski"),
              "AFRICAN", List.of("afrikanisch", "african", "أفريقي", "nigerianisch", "ghanaisch", "eritreisch"),
              "ITALIAN", List.of("italienisch", "italian", "إيطالي"),
              "GERMAN", List.of("deutsch", "german", "ألماني"),
              "PERSIAN", List.of("persisch", "iranisch", "persian", "iranian", "فارسي", "إيراني"),
              "BALKAN", List.of("balkan", "bosnisch", "serbisch", "kroatisch", "bulgarisch", "بلقاني")));

  private RuleBasedDraftParser() {}

  /**
   * Parses one note. {@code latinLocale} is the locale of a name written in Latin script (e.g. the
   * market's primary locale); Arabic script always yields {@code ar}.
   */
  public static ExtractedProduct parse(@Nullable String input, String latinLocale) {
    if (input == null || input.isBlank()) {
      return ExtractedProduct.empty();
    }
    String text = westernDigits(input.strip());
    StringBuilder rest = new StringBuilder(text);

    Long price = takePrice(rest);
    Size size = takeSize(rest);
    if (price == null) {
      price = takeFirst(rest, BARE_PRICE).map(RuleBasedDraftParser::cents).orElse(null);
    }
    String name = EDGE_PUNCTUATION.matcher(SPACES.matcher(rest).replaceAll(" ")).replaceAll("");
    Map<String, String> names = new LinkedHashMap<>();
    if (!name.isEmpty() && name.codePoints().anyMatch(Character::isLetter)) {
      String locale = ARABIC.matcher(name).find() ? "ar" : latinLocale;
      names.put(locale, capitalize(clip(name, 120)));
    }

    List<String> tokens = tokens(text);
    String category = bestMatch(tokens, CATEGORIES, CATEGORY_ORDER);
    List<String> allergens = Allergens.CODES.stream().filter(code -> ALLERGENS.containsKey(code) && matches(tokens, ALLERGENS.get(code)) > 0).toList();
    List<String> cuisines = CUISINES.keySet().stream().sorted().filter(code -> matches(tokens, CUISINES.get(code)) > 0).toList();

    return new ExtractedProduct(
        names,
        Map.of(),
        null,
        size == null ? null : size.label(),
        size == null ? null : size.netQuantity(),
        size == null ? null : size.netUnit(),
        category,
        cuisines,
        ORGANIC.matcher(text).find(),
        allergens,
        List.of(),
        price,
        null);
  }

  /** Pack size: display label in German notation plus the net quantity in kg, l or pieces. */
  record Size(String label, BigDecimal netQuantity, String netUnit) {}

  private static @Nullable Long takePrice(StringBuilder rest) {
    for (Pattern pattern : List.of(PRICE_AFTER, PRICE_BEFORE)) {
      var found = takeFirst(rest, pattern);
      if (found.isPresent()) {
        return cents(found.get());
      }
    }
    return null;
  }

  private static @Nullable Size takeSize(StringBuilder rest) {
    Matcher multi = MULTIPACK.matcher(rest);
    if (multi.find()) {
      int count = Integer.parseInt(multi.group(1));
      Size single = size(multi.group(2), multi.group(3));
      rest.replace(multi.start(), multi.end(), " ");
      if (count < 1) {
        return single;
      }
      return new Size(count + " × " + single.label(), single.netQuantity().multiply(BigDecimal.valueOf(count)).stripTrailingZeros(), single.netUnit());
    }
    Matcher matcher = SIZE.matcher(rest);
    if (matcher.find()) {
      Size size = size(matcher.group(1), matcher.group(2));
      rest.replace(matcher.start(), matcher.end(), " ");
      return size;
    }
    return null;
  }

  private static Size size(String number, String rawUnit) {
    BigDecimal amount = new BigDecimal(number.replace(',', '.'));
    String unit = rawUnit.toLowerCase(Locale.ROOT);
    return switch (unit) {
      case "kg", "kilo", "kilogramm", "kilogram", "كيلو", "كغ", "كجم", "كلغ" -> new Size(german(amount) + " kg", amount, "kg");
      case "g", "gr", "gramm", "gram", "grams", "غرام", "جرام", "غ", "جم" -> new Size(german(amount) + " g", thousandth(amount), "kg");
      case "l", "ltr", "liter", "litre", "لتر" -> new Size(german(amount) + " l", amount, "l");
      case "ml", "مل" -> new Size(german(amount) + " ml", thousandth(amount), "l");
      case "cl" -> new Size(german(amount) + " cl", amount.movePointLeft(2).stripTrailingZeros(), "l");
      default -> new Size(german(amount) + " Stück", amount, "piece");
    };
  }

  private static BigDecimal thousandth(BigDecimal amount) {
    return amount.movePointLeft(3).stripTrailingZeros();
  }

  private static String german(BigDecimal amount) {
    DecimalFormat format = new DecimalFormat("#,##0.###", DecimalFormatSymbols.getInstance(Locale.GERMANY));
    return format.format(amount);
  }

  private static long cents(String number) {
    return new BigDecimal(number.replace(',', '.')).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
  }

  private static Optional<String> takeFirst(StringBuilder rest, Pattern pattern) {
    Matcher matcher = pattern.matcher(rest);
    if (!matcher.find()) {
      return Optional.empty();
    }
    String value = matcher.group(1);
    rest.replace(matcher.start(), matcher.end(), " ");
    return Optional.of(value);
  }

  private static String westernDigits(String text) {
    StringBuilder out = new StringBuilder(text.length());
    text.codePoints()
        .forEach(
            cp -> {
              if (cp >= '٠' && cp <= '٩') {
                out.append((char) ('0' + (cp - '٠')));
              } else if (cp >= '۰' && cp <= '۹') {
                out.append((char) ('0' + (cp - '۰')));
              } else if (cp == '٫') {
                out.append(',');
              } else if (cp != '٬') {
                out.appendCodePoint(cp);
              }
            });
    return out.toString();
  }

  private static List<String> tokens(String text) {
    String normalized = TextNormalizer.normalize(text);
    List<String> tokens = new ArrayList<>(NON_LETTERS.splitAsStream(normalized).toList());
    tokens.removeIf(String::isEmpty);
    // Arabic definite article: "الخبز" also matches the keyword "خبز".
    List<String> withoutArticle =
        tokens.stream().filter(t -> t.startsWith("ال") && t.length() > 3).map(t -> t.substring(2)).toList();
    tokens.addAll(withoutArticle);
    // Two-word keywords ("olive oil", "فول سوداني") are matched against neighbouring pairs.
    int words = tokens.size();
    for (int i = 0; i + 1 < words; i++) {
      tokens.add(tokens.get(i) + " " + tokens.get(i + 1));
    }
    return tokens;
  }

  private static @Nullable String bestMatch(List<String> tokens, Map<String, List<String>> table, List<String> order) {
    String best = null;
    int bestScore = 0;
    for (String key : order) {
      int score = matches(tokens, table.get(key));
      if (score > bestScore) {
        best = key;
        bestScore = score;
      }
    }
    return best;
  }

  /**
   * Sum of the lengths of matched keywords. Keywords of four or more characters also match inside
   * a word ("hähnchenbrust", Arabic "الخبز"); shorter ones only as a whole word.
   */
  private static int matches(List<String> tokens, List<String> keywords) {
    int score = 0;
    for (String keyword : keywords) {
      boolean hit = keyword.length() >= 4 ? tokens.stream().anyMatch(t -> t.contains(keyword)) : tokens.contains(keyword);
      if (hit) {
        score += keyword.length();
      }
    }
    return score;
  }

  private static Map<String, List<String>> normalizedTable(Map<String, List<String>> table) {
    Map<String, List<String>> out = new LinkedHashMap<>();
    table.forEach(
        (key, words) -> {
          Set<String> normalized = new LinkedHashSet<>();
          words.forEach(word -> normalized.add(TextNormalizer.normalize(word)));
          out.put(key, List.copyOf(normalized));
        });
    return Map.copyOf(out);
  }

  private static String capitalize(String name) {
    int first = name.codePointAt(0);
    return Character.isLowerCase(first)
        ? new StringBuilder().appendCodePoint(Character.toUpperCase(first)).append(name, Character.charCount(first), name.length()).toString()
        : name;
  }

  private static String clip(String value, int max) {
    return value.length() <= max ? value : value.substring(0, max).strip();
  }
}
