package de.dukkan.delivery.domain;

import de.dukkan.shared.time.DukkanTime;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure slot calendar rules: Monday to Saturday, one-hour windows, for the next {@code daysAhead}
 * calendar days after today (Europe/Berlin). The order cutoff is {@code cutoffHour}:00 on the day
 * before the slot.
 */
public final class SlotPlanner {

  /** A slot that should exist. */
  public record PlannedSlot(Instant startsAt, Instant endsAt, Instant orderCutoffAt, long feeCents) {}

  private final int daysAhead;
  private final int firstSlotHour;
  private final int lastSlotHour;
  private final int cutoffHour;

  public SlotPlanner(int daysAhead, int firstSlotHour, int lastSlotHour, int cutoffHour) {
    this.daysAhead = daysAhead;
    this.firstSlotHour = firstSlotHour;
    this.lastSlotHour = lastSlotHour;
    this.cutoffHour = cutoffHour;
  }

  /** Slots for tomorrow up to and including today + {@code daysAhead}, Sundays excluded. */
  public List<PlannedSlot> plan(Instant now) {
    LocalDate today = LocalDate.ofInstant(now, DukkanTime.ZONE);
    List<PlannedSlot> slots = new ArrayList<>();
    for (int day = 1; day <= daysAhead; day++) {
      LocalDate date = today.plusDays(day);
      if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
        continue;
      }
      Instant cutoff = cutoffFor(date);
      for (int hour = firstSlotHour; hour <= lastSlotHour; hour++) {
        ZonedDateTime start = date.atTime(LocalTime.of(hour, 0)).atZone(DukkanTime.ZONE);
        slots.add(
            new PlannedSlot(
                start.toInstant(), start.plusHours(1).toInstant(), cutoff, feeForHour(hour)));
      }
    }
    return slots;
  }

  /** Order cutoff for slots on {@code deliveryDate}: {@code cutoffHour}:00 on the previous day. */
  public Instant cutoffFor(LocalDate deliveryDate) {
    return deliveryDate.minusDays(1).atTime(LocalTime.of(cutoffHour, 0)).atZone(DukkanTime.ZONE).toInstant();
  }

  /** The time range covered by {@link #plan(Instant)}. */
  public Instant horizonEnd(Instant now) {
    LocalDate today = LocalDate.ofInstant(now, DukkanTime.ZONE);
    return today.plusDays(daysAhead + 1L).atStartOfDay(DukkanTime.ZONE).toInstant();
  }

  /** Mock fee tiers: off-peak midday is free, mornings/afternoons 0.99, evenings 1.99. */
  public static long feeForHour(int startHour) {
    if (startHour >= 10 && startHour < 16) {
      return 0;
    }
    if (startHour >= 18) {
      return 199;
    }
    return 99;
  }
}
