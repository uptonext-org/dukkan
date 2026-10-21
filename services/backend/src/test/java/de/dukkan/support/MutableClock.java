package de.dukkan.support;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A test clock that can be moved. It starts on Monday 10:00 Europe/Berlin of the current week, so
 * weekday-dependent rules (slots, opening hours) are deterministic while seed data that Flyway
 * stores relative to {@code now()} ("new" listings, promotion validity) stays consistent.
 */
public final class MutableClock extends Clock {

  public static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

  /** Monday 10:00 Europe/Berlin of the current week. */
  public static final Instant DEFAULT = mondayTenOClock();

  private final AtomicReference<Instant> now = new AtomicReference<>(DEFAULT);

  @SuppressWarnings("TimeZoneUsage") // The single place in tests that reads the real date.
  private static Instant mondayTenOClock() {
    LocalDate today = LocalDate.now(Clock.systemUTC().withZone(BERLIN));
    return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atTime(LocalTime.of(10, 0)).atZone(BERLIN).toInstant();
  }

  public void set(Instant instant) {
    now.set(instant);
  }

  public void advance(Duration duration) {
    now.updateAndGet(i -> i.plus(duration));
  }

  public void reset() {
    now.set(DEFAULT);
  }

  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Instant instant() {
    return now.get();
  }
}
