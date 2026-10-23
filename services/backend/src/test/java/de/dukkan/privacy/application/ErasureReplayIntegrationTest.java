package de.dukkan.privacy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.support.IntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.context.TestPropertySource;

/** A restored backup must not bring deleted accounts back (GDPR Art. 17). */
@TestPropertySource(properties = "dukkan.privacy.erasure-journal=build/test-erasures/${random.uuid}.log")
class ErasureReplayIntegrationTest extends IntegrationTest {

  @Autowired ErasureJournal journal;
  @Autowired ErasureReplay replay;

  private void addAddress(String token) throws Exception {
    mvc.perform(
            json(
                auth(post("/api/v1/addresses"), token),
                toJson(Map.of("street", "Lange Reihe", "houseNumber", "12", "postalCode", "20099", "city", "Hamburg"))))
        .andExpect(status().isCreated());
  }

  @Test
  void aDeletionIsJournaledAndErasedAgainWhenARestoreBringsTheAccountBack() throws Exception {
    String deleted = registerUser();
    UUID deletedId = userId(deleted);
    mvc.perform(json(auth(delete("/api/v1/me"), deleted), toJson(Map.of("password", PASSWORD)))).andExpect(status().isNoContent());
    assertThat(journal.erasedAccounts()).contains(deletedId);

    // A restore from before the deletion: the account is back with its data. Stand-in: an active
    // account with an address whose erasure is in the journal.
    String restored = registerUser();
    UUID restoredId = userId(restored);
    addAddress(restored);
    journal.record(restoredId, clock.instant());

    replay.run(new DefaultApplicationArguments());

    assertThat(jdbc.sql("SELECT deleted_at IS NOT NULL FROM app_user WHERE id = ?").param(restoredId).query(Boolean.class).single()).isTrue();
    assertThat(jdbc.sql("SELECT email FROM app_user WHERE id = ?").param(restoredId).query(String.class).single()).endsWith("@deleted.invalid");
    assertThat(jdbc.sql("SELECT count(*) FROM address WHERE user_id = ?").param(restoredId).query(Long.class).single()).isZero();
    mvc.perform(auth(get("/api/v1/me"), restored)).andExpect(status().isUnauthorized());

    // Already erased accounts are left alone; a second start changes nothing.
    replay.run(new DefaultApplicationArguments());
  }
}
