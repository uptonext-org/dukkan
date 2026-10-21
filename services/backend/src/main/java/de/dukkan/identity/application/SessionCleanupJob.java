package de.dukkan.identity.application;

import de.dukkan.identity.infrastructure.SessionRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Nightly removal of expired refresh and password reset tokens (03:30 Europe/Berlin). */
@Component
class SessionCleanupJob {

  private static final Logger LOG = LoggerFactory.getLogger(SessionCleanupJob.class);

  private final SessionRepository sessions;
  private final Clock clock;

  SessionCleanupJob(SessionRepository sessions, Clock clock) {
    this.sessions = sessions;
    this.clock = clock;
  }

  @Scheduled(cron = "0 30 3 * * *", zone = "Europe/Berlin")
  @Transactional
  public void deleteExpired() {
    int deleted = sessions.deleteExpired(clock.instant());
    if (deleted > 0) {
      LOG.info("Deleted {} expired session token(s)", deleted);
    }
  }
}
