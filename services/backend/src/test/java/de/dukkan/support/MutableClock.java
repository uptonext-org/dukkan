package de.dukkan.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/** A test clock that can be moved forward. */
public final class MutableClock extends Clock {

  /** Monday 2026-10-19 10:00 Europe/Berlin. */
  public static final Instant DEFAULT = Instant.parse("2026-10-19T08:00:00Z");

  private final AtomicReference<Instant> now = new AtomicReference<>(DEFAULT);

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
