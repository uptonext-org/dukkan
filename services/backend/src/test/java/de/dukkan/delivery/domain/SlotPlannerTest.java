package de.dukkan.delivery.domain;

import static org.assertj.core.api.Assertions.assertThat;

import de.dukkan.delivery.domain.SlotPlanner.PlannedSlot;
import de.dukkan.shared.time.DukkanTime;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SlotPlannerTest {

  private final SlotPlanner planner = new SlotPlanner(7, 8, 20, 22);

  /** Monday 2026-10-19 10:00 Europe/Berlin (CEST, +02:00). */
  private final Clock monday = Clock.fixed(Instant.parse("2026-10-19T08:00:00Z"), ZoneOffset.UTC);

  @Test
  void plansMondayToSaturdayForTheNextSevenDays() {
    List<PlannedSlot> slots = planner.plan(monday.instant());

    // Tue 20 .. Mon 26 is 7 days; Sunday 25 is skipped -> 6 days * 13 hourly slots (08..20).
    assertThat(slots).hasSize(6 * 13);
    assertThat(slots)
        .extracting(s -> s.startsAt().atZone(DukkanTime.ZONE).getDayOfWeek())
        .doesNotContain(DayOfWeek.SUNDAY);
    ZonedDateTime first = slots.getFirst().startsAt().atZone(DukkanTime.ZONE);
    ZonedDateTime last = slots.getLast().startsAt().atZone(DukkanTime.ZONE);
    assertThat(first.toLocalDateTime()).hasToString("2026-10-20T08:00");
    assertThat(last.toLocalDateTime()).hasToString("2026-10-26T20:00");
    assertThat(slots).allSatisfy(s -> assertThat(s.endsAt()).isEqualTo(s.startsAt().plusSeconds(3600)));
  }

  @Test
  void isDeterministicSoGenerationCanBeIdempotent() {
    assertThat(planner.plan(monday.instant())).isEqualTo(planner.plan(monday.instant().plusSeconds(3600)));
  }

  @Test
  void cutoffIsTwentyTwoOClockOnThePreviousDay() {
    PlannedSlot tuesdayMorning = planner.plan(monday.instant()).getFirst();

    assertThat(tuesdayMorning.orderCutoffAt()).isEqualTo(Instant.parse("2026-10-19T20:00:00Z")); // 22:00 CEST
  }

  @Test
  void cutoffFollowsDaylightSavingTime() {
    // 2026-10-25 is the end of CEST; slots on Monday 26 (CET) have their cutoff on Sunday 25 22:00 CET.
    assertThat(planner.cutoffFor(LocalDate.of(2026, 10, 26))).isEqualTo(Instant.parse("2026-10-25T21:00:00Z"));
    assertThat(planner.cutoffFor(LocalDate.of(2026, 10, 25))).isEqualTo(Instant.parse("2026-10-24T20:00:00Z"));
  }

  @Test
  void afterCutoffTomorrowsSlotsArePast() {
    Instant lateMonday = Instant.parse("2026-10-19T20:30:00Z"); // 22:30 CEST
    PlannedSlot tuesdayMorning = planner.plan(lateMonday).getFirst();

    assertThat(lateMonday).isAfterOrEqualTo(tuesdayMorning.orderCutoffAt());
  }

  @Test
  void saturdayPlanSkipsSundayAndStartsMonday() {
    Instant saturday = Instant.parse("2026-10-24T08:00:00Z");
    List<PlannedSlot> slots = planner.plan(saturday);

    assertThat(slots.getFirst().startsAt().atZone(DukkanTime.ZONE).getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    assertThat(slots).hasSize(6 * 13); // Sun 25 .. Sat 31 without Sunday
  }

  @ParameterizedTest
  @CsvSource({"8, 99", "9, 99", "10, 0", "15, 0", "16, 99", "17, 99", "18, 199", "20, 199"})
  void feeTiers(int hour, long fee) {
    assertThat(SlotPlanner.feeForHour(hour)).isEqualTo(fee);
  }

  @Test
  void horizonEndsAtMidnightAfterTheLastDay() {
    assertThat(planner.horizonEnd(monday.instant())).isEqualTo(Instant.parse("2026-10-26T23:00:00Z"));
  }
}
