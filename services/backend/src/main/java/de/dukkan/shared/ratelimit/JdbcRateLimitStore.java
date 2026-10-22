package de.dukkan.shared.ratelimit;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Rate-limit counters in PostgreSQL (B-53), so a limit holds across all replicas. Each increment is
 * one upsert in its own transaction: an attempt counts even when the request it guards fails and
 * rolls back (a wrong password is exactly what has to be counted). Expired windows are removed
 * every hour.
 */
@Component
class JdbcRateLimitStore implements RateLimitStore {

  private static final Logger LOG = LoggerFactory.getLogger(JdbcRateLimitStore.class);

  private final JdbcClient jdbc;
  private final TransactionTemplate ownTransaction;
  private final Clock clock;

  JdbcRateLimitStore(JdbcClient jdbc, PlatformTransactionManager transactions, Clock clock) {
    this.jdbc = jdbc;
    this.ownTransaction = new TransactionTemplate(transactions);
    this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.clock = clock;
  }

  @Override
  public int increment(String bucket, String key, Instant windowStart, Instant expiresAt) {
    Integer count =
        ownTransaction.execute(
            status ->
                jdbc.sql(
                        """
                        INSERT INTO rate_limit_counter (bucket, key, window_start, count, expires_at)
                        VALUES (:bucket, :key, :windowStart, 1, :expiresAt)
                        ON CONFLICT (bucket, key, window_start) DO UPDATE SET count = rate_limit_counter.count + 1
                        RETURNING count
                        """)
                    .param("bucket", bucket)
                    .param("key", key.length() <= 200 ? key : key.substring(0, 200))
                    .param("windowStart", Timestamp.from(windowStart))
                    .param("expiresAt", Timestamp.from(expiresAt))
                    .query(Integer.class)
                    .single());
    return count == null ? 1 : count;
  }

  @Scheduled(cron = "0 7 * * * *")
  public void deleteExpired() {
    int deleted = jdbc.sql("DELETE FROM rate_limit_counter WHERE expires_at < :now").param("now", Timestamp.from(clock.instant())).update();
    if (deleted > 0) {
      LOG.debug("Deleted {} expired rate-limit counters", deleted);
    }
  }
}
