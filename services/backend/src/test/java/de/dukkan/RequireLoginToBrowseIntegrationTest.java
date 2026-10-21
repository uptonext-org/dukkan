package de.dukkan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** With {@code dukkan.features.require-login-to-browse=true} the catalog GETs answer 401 without a token. */
@TestPropertySource(properties = {"dukkan.features.require-login-to-browse=true", "dukkan.features.merchant-auto-approve=false"})
class RequireLoginToBrowseIntegrationTest extends IntegrationTest {

  @Test
  void catalogEndpointsNeedATokenWhenTheFlagIsOn() throws Exception {
    UUID anatolia = marketId(ANATOLIA);
    for (String path :
        new String[] {
          "/api/v1/markets?lat=53.55&lng=10.01",
          "/api/v1/markets/" + anatolia,
          "/api/v1/markets/" + anatolia + "/home",
          "/api/v1/markets/" + anatolia + "/categories",
          "/api/v1/markets/" + anatolia + "/products",
          "/api/v1/markets/" + anatolia + "/promotions",
          "/api/v1/markets/" + anatolia + "/search/suggestions?q=ap",
          "/api/v1/markets/" + anatolia + "/slots?type=PICKUP",
          "/api/v1/products/" + listingId(ANATOLIA, "Äpfel Elstar"),
          "/api/v1/search/products?q=apfel&lat=53.55&lng=10.01"
        }) {
      mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
    String token = registerUser();
    mvc.perform(auth(get("/api/v1/markets/" + anatolia + "/home"), token)).andExpect(status().isOk());
    mvc.perform(auth(get("/api/v1/markets").param("lat", "53.55").param("lng", "10.01"), token)).andExpect(status().isOk());
    // Languages, filter chips, address search and sign-up stay public.
    mvc.perform(get("/api/v1/locales")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/market-types")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/geo/search").param("q", "Lange Reihe")).andExpect(status().isOk());
  }

  @Test
  void withoutAutoApproveNewMarketsWaitForReview() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Wartender Testmarkt");
    mvc.perform(auth(get("/api/v1/merchant/markets"), merchant))
        .andExpect(jsonPath("$[?(@.id == '" + market + "')].status").value("PENDING_REVIEW"));
    mvc.perform(auth(get("/api/v1/markets/" + market), merchant)).andExpect(status().isNotFound());
  }
}
