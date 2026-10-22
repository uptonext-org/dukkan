package de.dukkan.shoppinglist.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.jspecify.annotations.Nullable;

/**
 * How much of one item the customer wants: a count of pieces or packs, or a weight or volume in
 * base units (grams, millilitres).
 */
public record Amount(BigDecimal value, Unit unit) {

  public enum Unit {
    /** Single items ("2 Gurken"); matched against "6 Stück" packs. */
    PIECE,
    /** Packs, bottles, cans, bunches: the number of listings to add. */
    PACK,
    GRAM,
    MILLILITRE
  }

  public static final Amount ONE = new Amount(BigDecimal.ONE, Unit.PIECE);

  public Amount {
    if (value.signum() <= 0) {
      throw new IllegalArgumentException("amount must be positive");
    }
  }

  /** Parses a value with a unit as people say it ({@code kg}, {@code Liter}, {@code Stück} …). */
  public static @Nullable Amount of(BigDecimal value, String unit) {
    if (value.signum() <= 0) {
      return null;
    }
    return switch (unit) {
      case "piece" -> new Amount(value, Unit.PIECE);
      case "pack" -> new Amount(value, Unit.PACK);
      case "g" -> new Amount(value, Unit.GRAM);
      case "kg" -> new Amount(value.multiply(BigDecimal.valueOf(1000)), Unit.GRAM);
      case "ml" -> new Amount(value, Unit.MILLILITRE);
      case "l" -> new Amount(value.multiply(BigDecimal.valueOf(1000)), Unit.MILLILITRE);
      default -> null;
    };
  }

  /** "1 kg", "500 g", "1.5 l"; {@code null} for counts, which the quantity already shows. */
  public @Nullable String label() {
    return switch (unit) {
      case PIECE, PACK -> null;
      case GRAM -> value.compareTo(BigDecimal.valueOf(1000)) >= 0 ? plain(value.movePointLeft(3)) + " kg" : plain(value) + " g";
      case MILLILITRE -> value.compareTo(BigDecimal.valueOf(1000)) >= 0 ? plain(value.movePointLeft(3)) + " l" : plain(value) + " ml";
    };
  }

  private static String plain(BigDecimal value) {
    return value.setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
  }
}
