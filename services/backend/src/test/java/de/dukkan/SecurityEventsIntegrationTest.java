package de.dukkan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.identity.application.IdentityService;
import de.dukkan.support.IntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** Security events land in the log (category de.dukkan.security) without e-mail addresses or passwords. */
@ExtendWith(OutputCaptureExtension.class)
class SecurityEventsIntegrationTest extends IntegrationTest {

  @Autowired private IdentityService identity;

  @Test
  void failedSignInsDeniedAccessAndAdminActionsAreLoggedWithoutPersonalData(CapturedOutput output) throws Exception {
    String email = uniqueEmail();
    String customer = register(email, "CUSTOMER");
    mvc.perform(json(post("/api/v1/auth/login"), toJson(Map.of("email", email, "password", "wrong-password-123"))))
        .andExpect(status().isUnauthorized());
    mvc.perform(auth(get("/api/v1/admin/markets"), customer)).andExpect(status().isForbidden());

    String adminEmail = uniqueEmail();
    register(adminEmail, "CUSTOMER");
    UUID adminId = identity.findByEmail(adminEmail).orElseThrow().id();
    identity.grantRole(adminId, "ADMIN");
    String admin = login(adminEmail);
    UUID market = createMarket(registerMerchant(), "Protokollmarkt");
    mvc.perform(json(auth(post("/api/v1/admin/markets/" + market + "/status"), admin), "{\"status\":\"SUSPENDED\"}"))
        .andExpect(status().isOk());

    assertThat(output.getAll())
        .contains("security: login failed")
        .contains("security: access denied")
        .contains("security: role ADMIN granted")
        .contains("security: admin market_SUSPENDED on " + market)
        .doesNotContain(email)
        .doesNotContain("wrong-password-123");
  }
}
