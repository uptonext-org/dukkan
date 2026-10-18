package de.dukkan.order.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class OrderNumbersTest {

  @Test
  void formatsWithBerlinDateAndSixDigitSequence() {
    assertThat(OrderNumbers.format(Instant.parse("2026-10-19T08:00:00Z"), 42)).isEqualTo("DK-20261019-000042");
  }

  @Test
  void usesTheBerlinCalendarDay() {
    // 23:30 UTC on the 18th is already the 19th in Berlin.
    assertThat(OrderNumbers.format(Instant.parse("2026-10-18T23:30:00Z"), 1)).isEqualTo("DK-20261019-000001");
  }
}
