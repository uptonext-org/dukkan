package de.dukkan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.identity.application.IdentityService;
import de.dukkan.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Platform admins review markets; only ACTIVE markets are visible to customers. */
class AdminIntegrationTest extends IntegrationTest {

  @Autowired private IdentityService identity;

  @Test
  void adminsSuspendAndApproveMarketsAndOthersAreForbidden() throws Exception {
    String admin = adminToken();
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Prüfung Testmarkt");
    String customer = registerUser();

    mvc.perform(auth(get("/api/v1/admin/markets"), merchant)).andExpect(status().isForbidden());
    mvc.perform(auth(get("/api/v1/admin/markets"), customer)).andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/admin/markets")).andExpect(status().isUnauthorized());

    assertThat(strings(mvc.perform(auth(get("/api/v1/admin/markets").param("status", "ACTIVE"), admin)).andReturn(), "$[*].id"))
        .contains(market.toString());

    mvc.perform(json(auth(post("/api/v1/admin/markets/" + market + "/status"), admin), "{\"status\":\"SUSPENDED\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUSPENDED"));
    mvc.perform(get("/api/v1/markets/" + market)).andExpect(status().isNotFound());
    assertThat(strings(mvc.perform(auth(get("/api/v1/merchant/markets"), merchant)).andReturn(), "$[*].status"))
        .contains("SUSPENDED");

    mvc.perform(json(auth(post("/api/v1/admin/markets/" + market + "/status"), admin), "{\"status\":\"ACTIVE\"}"))
        .andExpect(jsonPath("$.status").value("ACTIVE"));
    mvc.perform(get("/api/v1/markets/" + market)).andExpect(status().isOk());

    mvc.perform(json(auth(post("/api/v1/admin/markets/" + market + "/status"), admin), "{\"status\":\"DELETED\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(json(auth(post("/api/v1/admin/markets/" + UUID.randomUUID() + "/status"), admin), "{\"status\":\"ACTIVE\"}"))
        .andExpect(status().isNotFound());
  }

  private String adminToken() throws Exception {
    String email = uniqueEmail();
    register(email, "CUSTOMER");
    identity.grantRole(identity.findByEmail(email).orElseThrow().id(), "ADMIN");
    return login(email); // the role is in the next token
  }
}
