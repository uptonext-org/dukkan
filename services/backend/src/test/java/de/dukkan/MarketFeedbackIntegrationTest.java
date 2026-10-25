package de.dukkan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.support.IntegrationTest;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Customers write to a market only while its merchant takes feedback (merchant point 3). */
class MarketFeedbackIntegrationTest extends IntegrationTest {

  private void write(String customer, UUID market, Map<String, Object> body, int expectedStatus) throws Exception {
    mvc.perform(json(auth(post("/api/v1/markets/" + market + "/feedback"), customer), toJson(body))).andExpect(status().is(expectedStatus));
  }

  @Test
  void theMerchantSwitchesFeedbackOnAndReadsIt() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Feedback Markt");
    String customer = registerUser();

    // Off until the merchant switches it on.
    mvc.perform(get("/api/v1/markets/" + market + "/feedback"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(false))
        .andExpect(jsonPath("$.maxLength").value(1000));
    mvc.perform(json(auth(post("/api/v1/markets/" + market + "/feedback"), customer), toJson(Map.of("message", "Hallo"))))
        .andExpect(status().is(422))
        .andExpect(jsonPath("$.code").value("FEEDBACK_DISABLED"));

    mvc.perform(json(auth(put("/api/v1/merchant/markets/" + market + "/feedback/settings"), merchant), toJson(Map.of("enabled", true))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(true))
        .andExpect(jsonPath("$.feedback.totalItems").value(0));
    mvc.perform(get("/api/v1/markets/" + market + "/feedback")).andExpect(jsonPath("$.enabled").value(true));

    write(customer, market, Map.of("message", "  Bitte mehr Bio-Joghurt!  ", "rating", 4), 204);
    clock.advance(Duration.ofMinutes(5));
    write(customer, market, Map.of("message", "Sehr freundlich."), 204);
    // Validation: empty, too long, rating out of range; and guests may not write.
    write(customer, market, Map.of("message", " "), 400);
    write(customer, market, Map.of("message", "x".repeat(1001)), 400);
    write(customer, market, Map.of("message", "Hm", "rating", 6), 400);
    mvc.perform(json(post("/api/v1/markets/" + market + "/feedback"), toJson(Map.of("message", "anonym")))).andExpect(status().isUnauthorized());

    mvc.perform(auth(get("/api/v1/merchant/markets/" + market + "/feedback"), merchant))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.feedback.totalItems").value(2))
        .andExpect(jsonPath("$.feedback.items[0].message").value("Sehr freundlich."))
        .andExpect(jsonPath("$.feedback.items[0].rating").doesNotExist())
        .andExpect(jsonPath("$.feedback.items[1].message").value("Bitte mehr Bio-Joghurt!"))
        .andExpect(jsonPath("$.feedback.items[1].rating").value(4))
        .andExpect(jsonPath("$.feedback.items[1].customerName").value("Test"));

    // Only the owner reads and switches.
    String other = registerMerchant();
    mvc.perform(auth(get("/api/v1/merchant/markets/" + market + "/feedback"), other)).andExpect(status().isNotFound());
    mvc.perform(json(auth(put("/api/v1/merchant/markets/" + market + "/feedback/settings"), other), toJson(Map.of("enabled", false))))
        .andExpect(status().isNotFound());

    // Switched off again: no new messages, the old ones stay readable.
    mvc.perform(json(auth(put("/api/v1/merchant/markets/" + market + "/feedback/settings"), merchant), toJson(Map.of("enabled", false))))
        .andExpect(jsonPath("$.enabled").value(false))
        .andExpect(jsonPath("$.feedback.totalItems").value(2));
    write(customer, market, Map.of("message", "Noch da?"), 422);
  }

  @Test
  void aFewMessagesADayAndTheyLeaveWithTheAccount() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Grenzen Feedback Markt");
    mvc.perform(json(auth(put("/api/v1/merchant/markets/" + market + "/feedback/settings"), merchant), toJson(Map.of("enabled", true))))
        .andExpect(status().isOk());
    String customer = registerUser();
    for (int i = 0; i < 3; i++) {
      write(customer, market, Map.of("message", "Nachricht " + i), 204);
    }
    write(customer, market, Map.of("message", "Eine zu viel"), 429);

    mvc.perform(auth(get("/api/v1/me/export"), customer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.feedback.length()").value(3))
        .andExpect(jsonPath("$.feedback[0].marketId").value(market.toString()));
    mvc.perform(json(auth(delete("/api/v1/me"), customer), toJson(Map.of("password", PASSWORD)))).andExpect(status().isNoContent());
    mvc.perform(auth(get("/api/v1/merchant/markets/" + market + "/feedback"), merchant)).andExpect(jsonPath("$.feedback.totalItems").value(0));
    assertThat(jdbc.sql("SELECT count(*) FROM market_feedback WHERE market_id = :m").param("m", market).query(Long.class).single()).isZero();
  }
}
