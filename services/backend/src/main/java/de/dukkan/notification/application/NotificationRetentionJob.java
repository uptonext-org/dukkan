package de.dukkan.notification.application;

import de.dukkan.notification.infrastructure.NotificationRepository;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nightly removal of read inbox entries older than {@code dukkan.notifications.retention}
 * (default 180 days, 03:50 Europe/Berlin). Unread entries stay until they are read.
 */
@Component
class NotificationRetentionJob {

  private static final Logger LOG = LoggerFactory.getLogger(NotificationRetentionJob.class);

  private final NotificationRepository repository;
  private final Clock clock;
  private final Duration retention;

  NotificationRetentionJob(NotificationRepository repository, Clock clock, @Value("${dukkan.notifications.retention:P180D}") Duration retention) {
    this.repository = repository;
    this.clock = clock;
    this.retention = retention;
  }

  @Scheduled(cron = "0 50 3 * * *", zone = "Europe/Berlin")
  @Transactional
  public void deleteOld() {
    int deleted = repository.deleteReadBefore(clock.instant().minus(retention));
    if (deleted > 0) {
      LOG.info("Deleted {} read notification(s) older than {}", deleted, retention);
    }
  }
}
