package de.dukkan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.support.IntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class SecurityIntegrationTest extends IntegrationTest {

  private static final String SOME_ID = "00000000-0000-0000-0000-000000000001";

  @ParameterizedTest
  @CsvSource({
    "GET, /api/v1/me",
    "PATCH, /api/v1/me",
    "GET, /api/v1/carts",
    "GET, /api/v1/markets/" + SOME_ID + "/cart",
    "DELETE, /api/v1/markets/" + SOME_ID + "/cart",
    "GET, /api/v1/markets/" + SOME_ID + "/favorites",
    "GET, /api/v1/favorites/markets",
    "GET, /api/v1/addresses",
    "GET, /api/v1/orders",
    "POST, /api/v1/markets/" + SOME_ID + "/orders",
    "GET, /api/v1/merchant/markets",
  })
  void protectedEndpointsRequireAToken(String method, String path) throws Exception {
    mvc.perform(request(HttpMethod.valueOf(method), path))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.title").exists())
        .andExpect(jsonPath("$.type").exists());
  }

  @Test
  void invalidAndTamperedTokensAreRejected() throws Exception {
    mvc.perform(auth(get("/api/v1/carts"), "not-a-jwt"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

    String token = registerUser();
    String tampered = token.substring(0, token.length() - 4) + (token.endsWith("AAAA") ? "BBBB" : "AAAA");
    mvc.perform(auth(get("/api/v1/me"), tampered)).andExpect(status().isUnauthorized());
    // An invalid token is rejected even on public endpoints.
    mvc.perform(auth(get("/api/v1/locales"), tampered)).andExpect(status().isUnauthorized());
  }

  @Test
  void expiredTokensAreRejected() throws Exception {
    String token = registerUser();
    mvc.perform(auth(get("/api/v1/me"), token)).andExpect(status().isOk());

    clock.advance(java.time.Duration.ofDays(31)); // beyond the 30-day test token lifetime

    mvc.perform(auth(get("/api/v1/me"), token))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void publicEndpointsNeedNoToken() throws Exception {
    UUID anatolia = marketId(ANATOLIA);
    mvc.perform(get("/api/v1/locales")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/market-types")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/cuisines")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/geo/search").param("q", "Altona")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/markets").param("lat", "53.55").param("lng", "10.0")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/markets/" + anatolia)).andExpect(status().isOk());
    mvc.perform(get("/api/v1/markets/" + anatolia + "/home")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/markets/" + anatolia + "/products")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/markets/" + anatolia + "/slots").param("type", "PICKUP")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/search/products").param("q", "tee").param("lat", "53.55").param("lng", "10.0")).andExpect(status().isOk());
    mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
  }

  @Test
  void registrationIssuesRolesAndMerchantAccounts() throws Exception {
    MvcResult merchant =
        mvc.perform(
                json(
                    post("/api/v1/auth/register"),
                    toJson(Map.of("email", uniqueEmail(), "password", PASSWORD, "firstName", "M", "lastName", "Händler", "accountType", "MERCHANT", "preferredLocale", "ar"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").isNumber())
            .andExpect(jsonPath("$.user.preferredLocale").value("ar"))
            .andReturn();
    assertThat(strings(merchant, "$.user.roles")).containsExactly("CUSTOMER", "MERCHANT");
    mvc.perform(
            json(
                post("/api/v1/auth/register"),
                toJson(Map.of("email", uniqueEmail(), "password", PASSWORD, "firstName", "A", "lastName", "B", "accountType", "ADMIN"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("accountType"));
  }

  @Test
  void loginFailuresDoNotRevealWhetherTheEmailExists() throws Exception {
    String email = uniqueEmail();
    register(email, "CUSTOMER");
    MvcResult wrongPassword =
        mvc.perform(json(post("/api/v1/auth/login"), toJson(Map.of("email", email, "password", "wrong-password"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
            .andReturn();
    MvcResult unknownEmail =
        mvc.perform(json(post("/api/v1/auth/login"), toJson(Map.of("email", "nobody-" + email, "password", "wrong-password"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
            .andReturn();
    assertThat((String) read(unknownEmail, "$.detail")).isEqualTo(read(wrongPassword, "$.detail"));
    assertThat(login(email)).isNotBlank();
  }

  @Test
  void duplicateEmailIsAConflictRegardlessOfCase() throws Exception {
    String email = uniqueEmail();
    register(email, "CUSTOMER");
    Map<String, String> upper =
        Map.of("email", email.toUpperCase(java.util.Locale.ROOT), "password", PASSWORD, "firstName", "A", "lastName", "B");
    mvc.perform(json(post("/api/v1/auth/register"), toJson(upper)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
  }

  @Test
  void usersCannotAccessOtherUsersResources() throws Exception {
    String alice = registerUser();
    String bob = registerUser();
    String aliceAddress = createAddress(alice, "Kardamomweg", "2", "20099");
    UUID anatolia = marketId(ANATOLIA);

    mvc.perform(json(auth(put("/api/v1/addresses/" + aliceAddress), bob), toJson(Map.of("street", "X", "houseNumber", "1", "postalCode", "20099", "city", "Hamburg"))))
        .andExpect(status().isNotFound());
    mvc.perform(auth(delete("/api/v1/addresses/" + aliceAddress), bob)).andExpect(status().isNotFound());
    mvc.perform(auth(get("/api/v1/addresses"), bob)).andExpect(jsonPath("$.length()").value(0));

    String slot = availableSlot(slots(alice, anatolia, "DELIVERY", aliceAddress), 1);
    assertThat(reserve(bob, anatolia, "DELIVERY", slot, aliceAddress).getResponse().getStatus()).isEqualTo(404);

    String orderId = orderPickup(alice, marketId(BAKERY), listingId(BAKERY, "Franzbrötchen"), 4);
    mvc.perform(auth(get("/api/v1/orders/" + orderId), bob)).andExpect(status().isNotFound());
    mvc.perform(auth(post("/api/v1/orders/" + orderId + "/cancel"), bob)).andExpect(status().isNotFound());
    mvc.perform(auth(get("/api/v1/orders"), bob)).andExpect(jsonPath("$.length()").value(0));
    mvc.perform(auth(get("/api/v1/orders/" + UUID.randomUUID()), alice)).andExpect(status().isNotFound());
  }

  @Test
  void requestIdIsEchoedOrGenerated() throws Exception {
    mvc.perform(get("/api/v1/locales").header("X-Request-Id", "abc-123")).andExpect(header().string("X-Request-Id", "abc-123"));
    mvc.perform(get("/api/v1/locales").header("X-Request-Id", "bad id with spaces"))
        .andExpect(header().string("X-Request-Id", org.hamcrest.Matchers.matchesPattern("[0-9a-f-]{36}")));
    mvc.perform(get("/api/v1/carts"))
        .andExpect(header().exists("X-Request-Id"))
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
  }
}
