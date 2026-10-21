package de.dukkan.market.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpeningScheduleTest {

  private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

  /** Butcher: Mon–Fri 08:00–13:00 and 14:00–18:30, Sat 08:00–14:00, Sunday closed. */
  private static final OpeningSchedule BUTCHER = butcher();

  private static OpeningSchedule butcher() {
    List<OpeningSchedule.Interval> intervals = new ArrayList<>();
    for (DayOfWeek day : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
      intervals.add(new OpeningSchedule.Interval(day, LocalTime.of(14, 0), LocalTime.of(18, 30)));
      intervals.add(new OpeningSchedule.Interval(day, LocalTime.of(8, 0), LocalTime.of(13, 0)));
    }
    intervals.add(new OpeningSchedule.Interval(DayOfWeek.SATURDAY, LocalTime.of(8, 0), LocalTime.of(14, 0)));
    return new OpeningSchedule(intervals);
  }

  /** A fixed clock at a Berlin wall-clock time (2026-10-19 is a Monday). */
  private static Instant at(String localDateTime) {
    return Clock.fixed(LocalDateTime.parse(localDateTime).atZone(BERLIN).toInstant(), ZoneOffset.UTC).instant();
  }

  @Test
  void openDuringAnIntervalWithBothIntervalsAsTodaysHours() {
    Instant mondayMorning = at("2026-10-19T10:00");
    assertThat(BUTCHER.isOpen(mondayMorning)).isTrue();
    assertThat(BUTCHER.todayHours(mondayMorning)).contains("08:00–13:00, 14:00–18:30");
    assertThat(BUTCHER.nextOpening(mondayMorning)).isEmpty();
  }

  @Test
  void closedDuringTheLunchBreakAndReopensTheSameDay() {
    Instant lunch = at("2026-10-19T13:30");
    assertThat(BUTCHER.isOpen(lunch)).isFalse();
    assertThat(BUTCHER.nextOpening(lunch)).contains(at("2026-10-19T14:00"));
  }

  @Test
  void intervalsAreHalfOpen() {
    assertThat(BUTCHER.isOpen(at("2026-10-19T08:00"))).isTrue();
    assertThat(BUTCHER.isOpen(at("2026-10-19T18:30"))).isFalse();
    assertThat(BUTCHER.isOpen(at("2026-10-19T07:59"))).isFalse();
  }

  @Test
  void afterSaturdayClosingTheNextOpeningIsMonday() {
    Instant saturdayAfternoon = at("2026-10-24T15:00");
    assertThat(BUTCHER.isOpen(saturdayAfternoon)).isFalse();
    assertThat(BUTCHER.todayHours(saturdayAfternoon)).contains("08:00–14:00");
    // Sunday 25 October is also the end of daylight saving time.
    assertThat(BUTCHER.nextOpening(saturdayAfternoon)).contains(at("2026-10-26T08:00"));
    assertThat(BUTCHER.todayHours(at("2026-10-25T12:00"))).isEmpty();
  }

  @Test
  void usesBerlinLocalTimeNotUtc() {
    // 06:30 UTC = 08:30 CEST (summer) → open; 06:30 UTC = 07:30 CET (winter) → closed.
    assertThat(BUTCHER.isOpen(Instant.parse("2026-10-19T06:30:00Z"))).isTrue();
    assertThat(BUTCHER.isOpen(Instant.parse("2026-11-02T06:30:00Z"))).isFalse();
  }

  @Test
  void withoutHoursItIsNeverOpen() {
    OpeningSchedule none = new OpeningSchedule(List.of());
    assertThat(none.isOpen(at("2026-10-19T10:00"))).isFalse();
    assertThat(none.nextOpening(at("2026-10-19T10:00"))).isEmpty();
    assertThat(none.todayHours(at("2026-10-19T10:00"))).isEmpty();
  }

  /** Late shop: Fri 18:00–02:00, Sat 20:00–00:00, Sun 10:00–14:00. */
  private static final OpeningSchedule LATE =
      new OpeningSchedule(
          List.of(
              new OpeningSchedule.Interval(DayOfWeek.FRIDAY, LocalTime.of(18, 0), LocalTime.of(2, 0)),
              new OpeningSchedule.Interval(DayOfWeek.SATURDAY, LocalTime.of(20, 0), LocalTime.MIDNIGHT),
              new OpeningSchedule.Interval(DayOfWeek.SUNDAY, LocalTime.of(10, 0), LocalTime.of(14, 0))));

  @Test
  void overnightIntervalsStayOpenPastMidnight() {
    assertThat(LATE.isOpen(at("2026-10-23T23:30"))).isTrue(); // Friday night
    assertThat(LATE.isOpen(at("2026-10-24T01:59"))).isTrue(); // Saturday early, from Friday
    assertThat(LATE.isOpen(at("2026-10-24T02:00"))).isFalse();
    assertThat(LATE.isOpen(at("2026-10-24T23:59"))).isTrue(); // Saturday 20:00–00:00
    assertThat(LATE.isOpen(at("2026-10-25T00:00"))).isFalse();
    assertThat(LATE.todayHours(at("2026-10-23T12:00"))).contains("18:00–02:00");
    assertThat(LATE.nextOpening(at("2026-10-24T03:00"))).contains(at("2026-10-24T20:00"));
  }

  @Test
  void detectsOverlapsIncludingOvernightAndSundayIntoMonday() {
    assertThat(BUTCHER.hasOverlaps()).isFalse();
    assertThat(LATE.hasOverlaps()).isFalse();
    OpeningSchedule.Interval monday = new OpeningSchedule.Interval(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0));
    assertThat(
            new OpeningSchedule(
                    List.of(monday, new OpeningSchedule.Interval(DayOfWeek.MONDAY, LocalTime.of(11, 0), LocalTime.of(15, 0))))
                .hasOverlaps())
        .isTrue();
    assertThat(
            new OpeningSchedule(
                    List.of(
                        new OpeningSchedule.Interval(DayOfWeek.FRIDAY, LocalTime.of(18, 0), LocalTime.of(3, 0)),
                        new OpeningSchedule.Interval(DayOfWeek.SATURDAY, LocalTime.of(2, 0), LocalTime.of(10, 0))))
                .hasOverlaps())
        .isTrue();
    assertThat(
            new OpeningSchedule(
                    List.of(monday, new OpeningSchedule.Interval(DayOfWeek.SUNDAY, LocalTime.of(22, 0), LocalTime.of(9, 0))))
                .hasOverlaps())
        .isTrue();
    assertThat(
            new OpeningSchedule(
                    List.of(monday, new OpeningSchedule.Interval(DayOfWeek.SUNDAY, LocalTime.of(22, 0), LocalTime.of(8, 0))))
                .hasOverlaps())
        .isFalse();
  }
}
