package de.dukkan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

import de.dukkan.identity.application.IdentityService;
import de.dukkan.support.IntegrationTest;
import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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

  private long notifications(UUID userId) {
    return jdbc.sql("SELECT count(*) FROM notification WHERE user_id = :u").param("u", userId).query(Long.class).single();
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
                    }));
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
            });
    // The deletion is blocked by the handler's row lock.
    Thread.sleep(500);
    assertThat(deletion).isNotDone();

    write.countDown();
    handler.get(10, TimeUnit.SECONDS);
    assertThat(deletion.get(10, TimeUnit.SECONDS)).isEqualTo(204);
    assertThat(notifications(userId)).isZero();
    // Afterwards handlers see the account as gone and write nothing.
    Boolean stillActive = handlerTransaction.execute(status -> identity.holdActiveAccount(userId));
    assertThat(stillActive).isFalse();
  }
}
