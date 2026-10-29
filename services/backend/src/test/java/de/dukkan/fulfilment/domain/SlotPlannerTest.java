package de.dukkan.fulfilment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import de.dukkan.fulfilment.domain.SlotPlanner.OpeningWindow;
import de.dukkan.fulfilment.domain.SlotPlanner.PlannedSlot;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class SlotPlannerTest {

  private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
  private static final Duration DELIVERY_CUTOFF = Duration.ofHours(2);
  private static final Duration PICKUP_CUTOFF = Duration.ofHours(1);
  /** Monday 2026-10-19 10:00 Europe/Berlin. */
  private static final Instant MONDAY_10 = berlin("2026-10-19T10:00");

  private final SlotPlanner planner = new SlotPlanner(7, Duration.ofHours(1));

  private static Instant berlin(String localDateTime) {
    return LocalDateTime.parse(localDateTime).atZone(BERLIN).toInstant();
  }

  private static List<OpeningWindow> everyDay(String opens, String closes) {
    List<OpeningWindow> windows = new ArrayList<>();
    for (DayOfWeek day : DayOfWeek.values()) {
      windows.add(new OpeningWindow(day, LocalTime.parse(opens), LocalTime.parse(closes)));
    }
    return windows;
  }

  @Test
  void plansHourlyWindowsMondayToSaturdayForSevenDaysWithinOpeningHours() {
    List<PlannedSlot> slots = planner.plan(MONDAY_10, everyDay("09:00", "12:00"), DELIVERY_CUTOFF);

    // Today only the 11:00 window starts after now; Tue–Sat 3 windows each; Sunday is skipped.
    assertThat(slots).hasSize(1 + 5 * 3);
    assertThat(slots.getFirst().startsAt()).isEqualTo(berlin("2026-10-19T11:00"));
    assertThat(slots.getLast().startsAt()).isEqualTo(berlin("2026-10-24T11:00"));
    assertThat(slots).allSatisfy(s -> assertThat(Duration.between(s.startsAt(), s.endsAt())).isEqualTo(Duration.ofHours(1)));
    assertThat(slots)
        .extracting(s -> s.startsAt().atZone(BERLIN).getDayOfWeek())
        .doesNotContain(DayOfWeek.SUNDAY)
        .containsAll(EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.SATURDAY));
    assertThat(slots).isSortedAccordingTo(java.util.Comparator.comparing(PlannedSlot::startsAt));
  }

  @Test
  void deliveryCutoffIsTwoHoursAndPickupCutoffOneHourBeforeTheWindow() {
    PlannedSlot delivery = planner.plan(MONDAY_10, everyDay("09:00", "12:00"), DELIVERY_CUTOFF).getFirst();
    PlannedSlot pickup = planner.plan(MONDAY_10, everyDay("09:00", "12:00"), PICKUP_CUTOFF).getFirst();

    assertThat(delivery.orderCutoffAt()).isEqualTo(berlin("2026-10-19T09:00"));
    assertThat(pickup.orderCutoffAt()).isEqualTo(berlin("2026-10-19T10:00"));
  }

  @Test
  void respectsSeveralIntervalsPerDayAndDropsPartialWindows() {
    List<OpeningWindow> lunchBreak =
        List.of(
            new OpeningWindow(DayOfWeek.TUESDAY, LocalTime.of(8, 0), LocalTime.of(13, 30)),
            new OpeningWindow(DayOfWeek.TUESDAY, LocalTime.of(14, 0), LocalTime.of(16, 0)));

    List<PlannedSlot> slots = planner.plan(MONDAY_10, lunchBreak, PICKUP_CUTOFF);

    assertThat(slots)
        .extracting(s -> s.startsAt().atZone(BERLIN).toLocalTime())
        .containsExactly(
            LocalTime.of(8, 0), LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(11, 0), LocalTime.of(12, 0),
            LocalTime.of(14, 0), LocalTime.of(15, 0));
  }

  @Test
  void sundayOpeningHoursProduceNoSlots() {
    List<OpeningWindow> sundayOnly = List.of(new OpeningWindow(DayOfWeek.SUNDAY, LocalTime.of(8, 0), LocalTime.of(12, 0)));
    assertThat(planner.plan(MONDAY_10, sundayOnly, PICKUP_CUTOFF)).isEmpty();
  }

  @Test
  void horizonEndsAfterTheSeventhDay() {
    assertThat(planner.horizonEnd(MONDAY_10)).isEqualTo(berlin("2026-10-26T00:00"));
  }

  @Test
  void windowsKeepLocalTimeAcrossTheDaylightSavingChange() {
    // Saturday 24 Oct (CEST) and Monday 26 Oct (CET): 09:00 local each day.
    Instant saturday = berlin("2026-10-24T06:00");
    List<PlannedSlot> slots = planner.plan(saturday, everyDay("09:00", "10:00"), PICKUP_CUTOFF);
    assertThat(slots).extracting(PlannedSlot::startsAt).contains(Instant.parse("2026-10-24T07:00:00Z"), Instant.parse("2026-10-26T08:00:00Z"));
  }

  @Test
  void overnightWindowsGetSlotsAfterMidnightWithinTheHorizon() {
    List<OpeningWindow> friday = List.of(new OpeningWindow(DayOfWeek.FRIDAY, LocalTime.of(20, 0), LocalTime.of(1, 0)));

    List<PlannedSlot> slots = planner.plan(MONDAY_10, friday, PICKUP_CUTOFF);

    assertThat(slots).extracting(PlannedSlot::startsAt)
        .containsExactly(
            berlin("2026-10-23T20:00"), berlin("2026-10-23T21:00"), berlin("2026-10-23T22:00"), berlin("2026-10-23T23:00"), berlin("2026-10-24T00:00"));
    assertThat(slots.getLast().endsAt()).isEqualTo(berlin("2026-10-24T01:00"));
    // On the last planned day, slots after midnight would start beyond the horizon and are left out.
    SlotPlanner oneDay = new SlotPlanner(1, Duration.ofHours(1));
    List<PlannedSlot> lastDay = oneDay.plan(MONDAY_10, List.of(new OpeningWindow(DayOfWeek.MONDAY, LocalTime.of(23, 0), LocalTime.of(2, 0))), PICKUP_CUTOFF);
    assertThat(lastDay).extracting(PlannedSlot::startsAt).containsExactly(berlin("2026-10-19T23:00"));
  }

  @Test
  void yesterdaysOvernightIntervalStillHasItsSlotsAfterMidnight() {
    List<OpeningWindow> saturday = List.of(new OpeningWindow(DayOfWeek.SATURDAY, LocalTime.of(20, 0), LocalTime.of(3, 0)));
    // Sunday 00:30: the Saturday-night interval is still open.
    List<PlannedSlot> slots = planner.plan(berlin("2026-10-18T00:30"), saturday, PICKUP_CUTOFF);

    assertThat(slots).extracting(PlannedSlot::startsAt).startsWith(berlin("2026-10-18T01:00"), berlin("2026-10-18T02:00"));
    // The interval opened on Saturday: a Saturday holiday closes these slots, a Sunday one does not.
    assertThat(slots.getFirst().openingDay()).isEqualTo(java.time.LocalDate.of(2026, 10, 17));
  }
}
