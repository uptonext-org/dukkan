package de.dukkan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.identity.application.IdentityService;
import de.dukkan.support.IntegrationTest;
import de.dukkan.support.TestImages;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;

/** Platform admins switch the shopping list (and speaking, photo/video) on and off for everyone. */
class FeatureSwitchIntegrationTest extends IntegrationTest {

  @Autowired private IdentityService identity;

  @BeforeEach
  void defaults() {
    jdbc.sql("DELETE FROM platform_feature").update();
  }

  private String adminToken() throws Exception {
    String email = uniqueEmail();
    register(email, "CUSTOMER");
    identity.grantRole(identity.findByEmail(email).orElseThrow().id(), "ADMIN");
    return login(email);
  }

  private void switchFeature(String admin, String feature, boolean enabled) throws Exception {
    mvc.perform(json(auth(put("/api/v1/admin/features/" + feature), admin), "{\"enabled\":" + enabled + "}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$." + feature).value(enabled));
  }

  @Test
  void theShoppingListStartsOffAndAdminsSwitchItAndItsPartsOn() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Schalter-Markt");
    addListing(merchant, market, "Salatgurke", 69);
    String customer = registerUser();

    mvc.perform(get("/api/v1/features"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.SHOPPING_LIST").value(false))
        .andExpect(jsonPath("$.SHOPPING_LIST_VOICE").value(false))
        .andExpect(jsonPath("$.SHOPPING_LIST_MEDIA").value(false));
    mvc.perform(get("/api/v1/shopping-list")).andExpect(jsonPath("$.enabled").value(false)).andExpect(jsonPath("$.voice").value(false));
    mvc.perform(multipart("/api/v1/markets/" + market + "/shopping-list").param("text", "2 Gurken").header(HttpHeaders.AUTHORIZATION, "Bearer " + customer))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("FEATURE_DISABLED"));

    String admin = adminToken();
    // Speaking switched on first: still off for customers while the list itself is off.
    switchFeature(admin, "SHOPPING_LIST_VOICE", true);
    mvc.perform(get("/api/v1/features")).andExpect(jsonPath("$.SHOPPING_LIST_VOICE").value(false));
    mvc.perform(auth(get("/api/v1/admin/features"), admin)).andExpect(jsonPath("$.SHOPPING_LIST_VOICE").value(true));

    switchFeature(admin, "SHOPPING_LIST", true);
    mvc.perform(get("/api/v1/shopping-list"))
        .andExpect(jsonPath("$.enabled").value(true))
        .andExpect(jsonPath("$.voice").value(true))
        .andExpect(jsonPath("$.media").value(false));
    mvc.perform(multipart("/api/v1/markets/" + market + "/shopping-list").param("text", "2 Gurken").header(HttpHeaders.AUTHORIZATION, "Bearer " + customer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lines[0].text").value("2 Gurken"));
    // Photos and videos stay off.
    confirmEmail(customer);
    mvc.perform(
            multipart("/api/v1/markets/" + market + "/shopping-list")
                .file(new MockMultipartFile("images", "list.png", "image/png", TestImages.png(32, 32, java.awt.Color.WHITE)))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + customer))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("FEATURE_DISABLED"));
  }

  @Test
  void onlyAdminsSwitchFeatures() throws Exception {
    String customer = registerUser();
    mvc.perform(auth(get("/api/v1/admin/features"), customer)).andExpect(status().isForbidden());
    mvc.perform(json(auth(put("/api/v1/admin/features/SHOPPING_LIST"), customer), "{\"enabled\":true}")).andExpect(status().isForbidden());
    mvc.perform(json(put("/api/v1/admin/features/SHOPPING_LIST"), "{\"enabled\":true}")).andExpect(status().isUnauthorized());
    String admin = adminToken();
    mvc.perform(json(auth(put("/api/v1/admin/features/NO_SUCH_THING"), admin), "{\"enabled\":true}")).andExpect(status().isBadRequest());
  }
}
