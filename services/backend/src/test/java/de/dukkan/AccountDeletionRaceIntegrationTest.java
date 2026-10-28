package de.dukkan;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

import de.dukkan.identity.application.IdentityService;
import de.dukkan.support.IntegrationTest;
import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * An event handler that writes for a user while that user deletes their account: the deletion
 * waits for the handler and then removes what it wrote, so nothing personal is left behind.
 */
class AccountDeletionRaceIntegrationTest extends IntegrationTest {

  @Autowired private IdentityService identity;
  @Autowired private PlatformTransactionManager transactionManager;

  // Own threads: the common pool has a single thread on a two-core CI runner, where the second
  // task would wait behind the first, which is blocked on purpose.
  private final ExecutorService threads = Executors.newFixedThreadPool(2);

  @AfterEach
  void stopThreads() {
    threads.shutdownNow();
  }

  private long notifications(UUID userId) {
    return jdbc.sql("SELECT count(*) FROM notification WHERE user_id = :u").param("u", userId).query(Long.class).single();
  }

  private boolean waitsForLock(String statement) throws InterruptedException {
    for (int i = 0; i < 100; i++) {
      long waiting =
          jdbc.sql("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock' AND query LIKE :q")
              .param("q", "%" + statement + "%")
              .query(Long.class)
              .single();
      if (waiting > 0) {
        return true;
      }
      Thread.sleep(50);
    }
    return false;
  }

  @Test
  void aDeletionWaitsForAHandlerWritingForTheSameUser() throws Exception {
    String customer = registerUser();
    UUID userId = userId(customer);
    TransactionTemplate handlerTransaction = new TransactionTemplate(transactionManager);
    handlerTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch write = new CountDownLatch(1);

    CompletableFuture<Void> handler =
        CompletableFuture.runAsync(
            () ->
                handlerTransaction.executeWithoutResult(
                    status -> {
                      assertThat(identity.holdActiveAccount(userId)).isTrue();
                      locked.countDown();
                      try {
                        assertThat(write.await(10, TimeUnit.SECONDS)).isTrue();
                      } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                      }
                      jdbc.sql(
                              "INSERT INTO notification (id, user_id, kind, title, body, created_at)"
                                  + " VALUES (:id, :u, 'NEW_ORDER', 'Titel', 'Text', :now)")
                          .param("id", UUID.randomUUID())
                          .param("u", userId)
                          .param("now", Timestamp.from(clock.instant()))
                          .update();
                    }),
            threads);
    assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

    CompletableFuture<Integer> deletion =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                return mvc.perform(json(auth(delete("/api/v1/me"), customer), toJson(Map.of("password", PASSWORD))))
                    .andReturn()
                    .getResponse()
                    .getStatus();
              } catch (Exception e) {
                throw new IllegalStateException(e);
              }
            },
            threads);
    // The deletion waits on the handler's row lock (seen by the database, not guessed from timing).
    assertThat(waitsForLock("FOR NO KEY UPDATE")).isTrue();
    assertThat(deletion).isNotDone();

    write.countDown();
    handler.get(10, TimeUnit.SECONDS);
    assertThat(deletion.get(10, TimeUnit.SECONDS)).isEqualTo(204);
    assertThat(notifications(userId)).isZero();
    // Afterwards handlers see the account as gone and write nothing.
    Boolean stillActive = handlerTransaction.execute(status -> identity.holdActiveAccount(userId));
    assertThat(stillActive).isFalse();
  }

  @Test
  void theDeletionLockDoesNotBlockRowsThatOnlyReferenceTheUser() throws Exception {
    // A token refresh inserts a refresh_token row (a foreign key to app_user) while the deletion
    // holds its lock; with FOR UPDATE the two would deadlock, with FOR NO KEY UPDATE the insert passes.
    UUID userId = userId(registerUser());
    TransactionTemplate deletion = new TransactionTemplate(transactionManager);
    deletion.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CompletableFuture<Void> holder =
        CompletableFuture.runAsync(
            () ->
                deletion.executeWithoutResult(
                    status -> {
                      identity.lockForDeletion(userId);
                      locked.countDown();
                      try {
                        assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
                      } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                      }
                    }),
            threads);
    assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
    try {
      CompletableFuture<Integer> insert =
          CompletableFuture.supplyAsync(
              () ->
                  jdbc.sql(
                          "INSERT INTO refresh_token (id, user_id, family_id, token_hash, created_at, expires_at)"
                              + " VALUES (:id, :u, :id, :hash, now(), now() + interval '1 day')")
                      .param("id", UUID.randomUUID())
                      .param("u", userId)
                      .param("hash", UUID.randomUUID().toString().getBytes(UTF_8))
                      .update(),
              threads);
      assertThat(insert.get(5, TimeUnit.SECONDS)).isEqualTo(1);
    } finally {
      release.countDown();
      holder.get(10, TimeUnit.SECONDS);
    }
  }
}
