package de.dukkan.fulfilment.domain;

import de.dukkan.shared.time.DukkanTime;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure slot calendar: for today and the following {@code daysAhead - 1} days (Europe/Berlin),
 * Monday to Saturday, consecutive windows of {@code slotLength} inside each opening interval. Only
 * windows that start after {@code now} are planned. The order cutoff is {@code cutoff} before the
 * window starts.
 */
public final class SlotPlanner {

  /** An opening interval on a weekday. */
  public record OpeningWindow(DayOfWeek day, LocalTime opens, LocalTime closes) {}

  /** A slot that should exist. */
  public record PlannedSlot(Instant startsAt, Instant endsAt, Instant orderCutoffAt) {}

  /** The longest horizon a market can choose. */
  public static final int MAX_DAYS_AHEAD = 14;

  private final int daysAhead;
  private final Duration slotLength;

  public SlotPlanner(int daysAhead, Duration slotLength) {
    this.daysAhead = daysAhead;
    this.slotLength = slotLength;
  }

  public List<PlannedSlot> plan(Instant now, List<OpeningWindow> openingHours, Duration cutoff) {
    LocalDate today = LocalDate.ofInstant(now, DukkanTime.ZONE);
    Instant horizon = horizonEnd(now);
    List<PlannedSlot> slots = new ArrayList<>();
    for (int day = 0; day < daysAhead; day++) {
      LocalDate date = today.plusDays(day);
      if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
        continue;
      }
      for (OpeningWindow window : openingHours) {
        if (window.day() != date.getDayOfWeek()) {
          continue;
        }
        ZonedDateTime start = date.atTime(window.opens()).atZone(DukkanTime.ZONE);
        // A closing time not after the opening time means the window ends on the next day.
        LocalDate closeDate = window.closes().isAfter(window.opens()) ? date : date.plusDays(1);
        ZonedDateTime close = closeDate.atTime(window.closes()).atZone(DukkanTime.ZONE);
        while (!start.plus(slotLength).isAfter(close)) {
          ZonedDateTime end = start.plus(slotLength);
          if (start.toInstant().isAfter(now) && start.toInstant().isBefore(horizon)) {
            slots.add(new PlannedSlot(start.toInstant(), end.toInstant(), start.toInstant().minus(cutoff)));
          }
          start = end;
        }
      }
    }
    slots.sort(Comparator.comparing(PlannedSlot::startsAt));
    return slots;
  }

  /** End of the planning horizon (start of the day after the last planned day). */
  public Instant horizonEnd(Instant now) {
    return LocalDate.ofInstant(now, DukkanTime.ZONE).plusDays(daysAhead).atStartOfDay(DukkanTime.ZONE).toInstant();
  }
}
