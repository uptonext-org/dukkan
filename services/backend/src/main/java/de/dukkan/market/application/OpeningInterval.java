package de.dukkan.market.application;

import java.time.LocalTime;

/** One opening interval in local time (Europe/Berlin); {@code dayOfWeek} 1 = Monday. */
public record OpeningInterval(int dayOfWeek, LocalTime opensAt, LocalTime closesAt) {}
