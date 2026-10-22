package de.dukkan.shoppinglist.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * How many units of a listing cover what the customer asked for: "1 kg Tomaten" with a 500 g pack
 * is 2, "8 Eier" with "6 Stück" is 2, "2 Gurken" sold per piece is 2. Without a comparable size the
 * count is taken as is (weights and volumes then mean one pack).
 */
public final class PackCount {

  /** "500 g", "1,5 l", "6 Stück", "2 x 250 g", "ca. 1 kg". */
  private static final Pattern SIZE =
      Pattern.compile("(?:(\\d+)\\s*[x×]\\s*)?(\\d+(?:[.,]\\d+)?)\\s*(kg|g|l|ml|cl|stück|stk|st\\.?|pcs|pieces?)\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private PackCount() {}

  public static int of(Amount amount, @Nullable String unitSize, int maxOrderQuantity) {
    int max = Math.max(1, maxOrderQuantity);
    return Math.clamp(packs(amount, size(unitSize)), 1, max);
  }

  private static int packs(Amount amount, @Nullable Amount pack) {
    if (amount.unit() == Amount.Unit.PACK) {
      return ceil(amount.value(), BigDecimal.ONE);
    }
    if (pack != null && pack.unit() == amount.unit()) {
      return ceil(amount.value(), pack.value());
    }
    return amount.unit() == Amount.Unit.PIECE ? ceil(amount.value(), BigDecimal.ONE) : 1;
  }

  private static int ceil(BigDecimal value, BigDecimal per) {
    BigDecimal packs = value.divide(per, 0, RoundingMode.CEILING);
    return packs.compareTo(BigDecimal.valueOf(1000)) > 0 ? 1000 : Math.max(1, packs.intValue());
  }

  static @Nullable Amount size(@Nullable String unitSize) {
    if (unitSize == null) {
      return null;
    }
    Matcher matcher = SIZE.matcher(unitSize);
    if (!matcher.find()) {
      return null;
    }
    BigDecimal value = new BigDecimal(matcher.group(2).replace(',', '.'));
    if (matcher.group(1) != null) {
      value = value.multiply(new BigDecimal(matcher.group(1)));
    }
    String unit = matcher.group(3).toLowerCase(Locale.ROOT);
    return switch (unit) {
      case "kg", "g", "l", "ml" -> Amount.of(value, unit);
      case "cl" -> Amount.of(value.multiply(BigDecimal.TEN), "ml");
      default -> Amount.of(value, "piece");
    };
  }
}
