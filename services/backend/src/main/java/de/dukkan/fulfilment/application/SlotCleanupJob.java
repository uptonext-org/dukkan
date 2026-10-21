package de.dukkan.fulfilment.application;

import de.dukkan.fulfilment.infrastructure.FulfilmentSlotRepository;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Slots are generated on demand and would otherwise accumulate forever. Once a day, slots that
 * ended more than a day ago and are used by no order or cart are deleted.
 */
@Component
public class SlotCleanupJob {

  private static final Logger LOG = LoggerFactory.getLogger(SlotCleanupJob.class);
  private static final Duration RETENTION = Duration.ofDays(1);

  private final FulfilmentSlotRepository slots;
  private final Clock clock;

  public SlotCleanupJob(FulfilmentSlotRepository slots, Clock clock) {
    this.slots = slots;
    this.clock = clock;
  }

  /** Runs nightly at 03:15 Berlin time; also callable directly (tests, operations). */
  @Scheduled(cron = "${dukkan.fulfilment.cleanup-cron:0 15 3 * * *}", zone = "Europe/Berlin")
  @Transactional
  public int deleteExpiredSlots() {
    int deleted = slots.deleteUnusedEndedBefore(clock.instant().minus(RETENTION));
    if (deleted > 0) {
      LOG.info("Deleted {} expired, unused delivery/pickup slots", deleted);
    }
    return deleted;
  }
}
