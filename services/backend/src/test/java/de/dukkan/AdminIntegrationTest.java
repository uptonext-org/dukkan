package de.dukkan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.identity.application.IdentityService;
import de.dukkan.support.IntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

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

  @Test
  void aMarketWithIncompleteSellerInformationCannotBeApproved() throws Exception {
    String admin = adminToken();
    String merchant = registerMerchant();
    Map<String, Object> profile = marketProfile("Lückenhaft Markt");
    profile.remove("phone");
    profile.put("addressLine", "Irgendwo in Hamburg");
    profile.put("legalName", "Lückenhaft");
    UUID market =
        UUID.fromString(
            read(
                mvc.perform(json(auth(post("/api/v1/merchant/markets"), merchant), toJson(profile))).andExpect(status().isCreated()).andReturn(),
                "$.id"));
    mvc.perform(json(auth(post("/api/v1/admin/markets/" + market + "/status"), admin), "{\"status\":\"PENDING_REVIEW\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.review.complete").value(false));

    MvcResult listed = mvc.perform(auth(get("/api/v1/admin/markets").param("status", "PENDING_REVIEW"), admin)).andReturn();
    String item = "$[?(@.id == '" + market + "')].review.items[?(@.code == '%s')].ok";
    assertThat(booleans(listed, item.formatted("LEGAL_NAME"))).containsExactly(false);
    assertThat(booleans(listed, item.formatted("POSTAL_ADDRESS"))).containsExactly(false);
    assertThat(booleans(listed, item.formatted("PHONE"))).containsExactly(false);
    assertThat(booleans(listed, item.formatted("REGISTER_INFO"))).containsExactly(false);

    mvc.perform(json(auth(post("/api/v1/admin/markets/" + market + "/status"), admin), "{\"status\":\"ACTIVE\"}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("MARKET_INCOMPLETE"));
    mvc.perform(get("/api/v1/markets/" + market)).andExpect(status().isNotFound());

    // The merchant completes the imprint; now it can be approved.
    Map<String, Object> complete = marketProfile("Lückenhaft Markt");
    complete.put("legalName", "Lückenhaft Markt e.K.");
    complete.put("registerInfo", "Amtsgericht Hamburg HRA 123456");
    mvc.perform(json(auth(put("/api/v1/merchant/markets/" + market), merchant), toJson(complete)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.review.complete").value(true));
    mvc.perform(json(auth(post("/api/v1/admin/markets/" + market + "/status"), admin), "{\"status\":\"ACTIVE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));
  }

  private String adminToken() throws Exception {
    String email = uniqueEmail();
    register(email, "CUSTOMER");
    identity.grantRole(identity.findByEmail(email).orElseThrow().id(), "ADMIN");
    return login(email); // the role is in the next token
  }
}
