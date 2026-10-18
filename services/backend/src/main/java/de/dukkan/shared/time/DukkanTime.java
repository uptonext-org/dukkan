package de.dukkan.shared.time;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** Business time zone helpers. All delivery rules are evaluated in {@code Europe/Berlin}. */
public final class DukkanTime {

  public static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

  private DukkanTime() {}

  /** Converts an instant to an offset date-time in the business time zone for API output. */
  public static OffsetDateTime toApi(Instant instant) {
    return OffsetDateTime.ofInstant(instant, ZONE);
  }
}
