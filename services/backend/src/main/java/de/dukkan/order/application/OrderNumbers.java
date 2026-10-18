package de.dukkan.order.application;

import de.dukkan.shared.time.DukkanTime;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Formats order numbers as {@code DK-yyyyMMdd-NNNNNN}. */
final class OrderNumbers {

  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT).withZone(DukkanTime.ZONE);

  private OrderNumbers() {}

  static String format(Instant placedAt, long sequence) {
    return "DK-" + DATE.format(placedAt) + "-" + String.format(Locale.ROOT, "%06d", sequence % 1_000_000);
  }
}
