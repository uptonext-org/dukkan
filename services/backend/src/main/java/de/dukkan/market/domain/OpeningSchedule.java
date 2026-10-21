package de.dukkan.market.domain;

import de.dukkan.shared.time.DukkanTime;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Weekly opening hours in {@code Europe/Berlin}; a day may have several intervals (e.g. a lunch
 * break). Intervals end on the same day (closing after midnight is not modelled).
 */
public final class OpeningSchedule {

  /** One opening interval: {@code [opens, closes)} on {@code day}. */
  public record Interval(DayOfWeek day, LocalTime opens, LocalTime closes) {
    public Interval {
      if (!closes.isAfter(opens)) {
        throw new IllegalArgumentException("closes must be after opens");
      }
    }
  }

  private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
  private static final int SEARCH_DAYS = 8;

  private final List<Interval> intervals;

  public OpeningSchedule(List<Interval> intervals) {
    this.intervals =
        intervals.stream()
            .sorted(Comparator.comparing(Interval::day).thenComparing(Interval::opens))
            .toList();
  }

  public List<Interval> intervals() {
    return intervals;
  }

  public List<Interval> on(DayOfWeek day) {
    return intervals.stream().filter(i -> i.day() == day).toList();
  }

  public boolean isOpen(Instant now) {
    ZonedDateTime local = now.atZone(DukkanTime.ZONE);
    LocalTime time = local.toLocalTime();
    return on(local.getDayOfWeek()).stream().anyMatch(i -> !time.isBefore(i.opens()) && time.isBefore(i.closes()));
  }

  /** Today's hours, e.g. {@code 08:00–13:00, 14:00–18:30}; empty when closed all day. */
  public Optional<String> todayHours(Instant now) {
    List<Interval> today = on(now.atZone(DukkanTime.ZONE).getDayOfWeek());
    if (today.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        today.stream().map(i -> HH_MM.format(i.opens()) + "–" + HH_MM.format(i.closes())).collect(Collectors.joining(", ")));
  }

  /** The next time the market opens after {@code now}; empty while it is open or without hours. */
  public Optional<Instant> nextOpening(Instant now) {
    if (intervals.isEmpty() || isOpen(now)) {
      return Optional.empty();
    }
    LocalDate today = now.atZone(DukkanTime.ZONE).toLocalDate();
    for (int offset = 0; offset < SEARCH_DAYS; offset++) {
      LocalDate date = today.plusDays(offset);
      for (Interval interval : on(date.getDayOfWeek())) {
        Instant opening = date.atTime(interval.opens()).atZone(DukkanTime.ZONE).toInstant();
        if (opening.isAfter(now)) {
          return Optional.of(opening);
        }
      }
    }
    return Optional.empty();
  }
}
