package de.dukkan.order.application;

import de.dukkan.order.infrastructure.OrderRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Data minimisation (GDPR Art. 5(1)(c, e)): nightly, removes the delivery address and notes from
 * finished orders older than {@code dukkan.privacy.order-address-retention} (an ISO period such as
 * {@code P3Y}). Off when empty. The period is the operator's decision (docs/operations/datensicherung.md).
 */
@Component
public class OrderAddressRetentionJob {

  private static final Logger LOG = LoggerFactory.getLogger(OrderAddressRetentionJob.class);

  private final OrderRepository orders;
  private final Clock clock;
  private final @Nullable Period retention;

  OrderAddressRetentionJob(
      OrderRepository orders, Clock clock, @Value("${dukkan.privacy.order-address-retention:}") @Nullable String retention) {
    this.orders = orders;
    this.clock = clock;
    this.retention = retention == null || retention.isBlank() ? null : Period.parse(retention.trim());
    if (this.retention != null && this.retention.isNegative()) {
      throw new IllegalArgumentException("dukkan.privacy.order-address-retention must not be negative");
    }
  }

  @Scheduled(cron = "0 0 4 * * *", zone = "Europe/Berlin")
  @Transactional
  public int minimise() {
    if (retention == null) {
      return 0;
    }
    Instant cutoff = clock.instant().atZone(ZoneOffset.UTC).minus(retention).toInstant();
    int changed = orders.minimiseFinishedBefore(cutoff);
    if (changed > 0) {
      LOG.info("Removed the delivery address from {} order(s) placed before {}", changed, cutoff);
    }
    return changed;
  }
}
