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
 * break). An interval whose closing time is not after its opening time runs past midnight and
 * ends on the next day ({@code 18:00–02:00}, {@code 20:00–00:00}).
 */
public final class OpeningSchedule {

  /** One opening interval starting on {@code day}: {@code [opens, closes)}, possibly overnight. */
  public record Interval(DayOfWeek day, LocalTime opens, LocalTime closes) {
    public Interval {
      if (closes.equals(opens)) {
        throw new IllegalArgumentException("closes must differ from opens");
      }
    }

    /** True when the interval ends on the next day. */
    public boolean overnight() {
      return !closes.isAfter(opens);
    }

    /** Start in minutes since Monday 00:00. */
    int startMinute() {
      return (day.getValue() - 1) * MINUTES_PER_DAY + opens.toSecondOfDay() / 60;
    }

    /** Length in minutes (at most just under 24 hours). */
    int lengthMinutes() {
      int length = (closes.toSecondOfDay() - opens.toSecondOfDay()) / 60;
      return overnight() ? length + MINUTES_PER_DAY : length;
    }
  }

  private static final int MINUTES_PER_DAY = 24 * 60;
  private static final int MINUTES_PER_WEEK = 7 * MINUTES_PER_DAY;

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
    boolean today =
        on(local.getDayOfWeek()).stream()
            .anyMatch(i -> !time.isBefore(i.opens()) && (i.overnight() || time.isBefore(i.closes())));
    boolean sinceYesterday =
        on(local.getDayOfWeek().minus(1)).stream().anyMatch(i -> i.overnight() && time.isBefore(i.closes()));
    return today || sinceYesterday;
  }

  /**
   * True when two intervals share a minute of the week, including an overnight interval running
   * into the next day's (and Sunday's into Monday's) hours.
   */
  public boolean hasOverlaps() {
    List<Interval> sorted = intervals.stream().sorted(Comparator.comparingInt(Interval::startMinute)).toList();
    if (sorted.size() < 2) {
      return false;
    }
    for (int i = 0; i < sorted.size(); i++) {
      Interval current = sorted.get(i);
      Interval next = sorted.get((i + 1) % sorted.size());
      int end = current.startMinute() + current.lengthMinutes();
      int nextStart = next.startMinute() + (i + 1 == sorted.size() ? MINUTES_PER_WEEK : 0);
      if (end > nextStart) {
        return true;
      }
    }
    return false;
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
