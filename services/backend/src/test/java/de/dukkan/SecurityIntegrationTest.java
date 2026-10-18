package de.dukkan;

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

  @ParameterizedTest
  @CsvSource({"GET, /api/v1/me", "GET, /api/v1/cart", "DELETE, /api/v1/cart", "GET, /api/v1/addresses", "GET, /api/v1/orders", "POST, /api/v1/orders"})
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
    mvc.perform(auth(get("/api/v1/cart"), "not-a-jwt"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

    String token = registerUser();
    String tampered = token.substring(0, token.length() - 4) + (token.endsWith("AAAA") ? "BBBB" : "AAAA");
    mvc.perform(auth(get("/api/v1/me"), tampered)).andExpect(status().isUnauthorized());
  }

  @Test
  void expiredTokensAreRejected() throws Exception {
    String token = registerUser();
    mvc.perform(auth(get("/api/v1/me"), token)).andExpect(status().isOk());

    clock.advance(java.time.Duration.ofHours(2)); // beyond the 1h token lifetime

    mvc.perform(auth(get("/api/v1/me"), token))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void publicEndpointsNeedNoToken() throws Exception {
    mvc.perform(get("/api/v1/categories")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/products")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/promotions")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/search/suggestions").param("q", "ap")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/delivery-areas/check").param("postalCode", "41460")).andExpect(status().isOk());
    mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
  }

  @Test
  void loginFailuresDoNotRevealWhetherTheEmailExists() throws Exception {
    String email = uniqueEmail();
    mvc.perform(
            post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("email", email, "password", "correct-horse-battery", "firstName", "A", "lastName", "B"))))
        .andExpect(status().isCreated());

    MvcResult wrongPassword =
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(Map.of("email", email, "password", "wrong-password"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
            .andReturn();
    MvcResult unknownEmail =
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(Map.of("email", "nobody-" + email, "password", "wrong-password"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
            .andReturn();
    org.assertj.core.api.Assertions.assertThat((String) read(unknownEmail, "$.detail"))
        .isEqualTo(read(wrongPassword, "$.detail"));
  }

  @Test
  void duplicateEmailIsAConflictRegardlessOfCase() throws Exception {
    String email = uniqueEmail();
    Map<String, String> body = Map.of("email", email, "password", "correct-horse-battery", "firstName", "A", "lastName", "B");
    mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
        .andExpect(status().isCreated());
    Map<String, String> upper =
        Map.of("email", email.toUpperCase(java.util.Locale.ROOT), "password", "correct-horse-battery", "firstName", "A", "lastName", "B");
    mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(toJson(upper)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
  }

  @Test
  void usersCannotAccessOtherUsersResources() throws Exception {
    String alice = registerUser();
    String bob = registerUser();
    String aliceAddress = createAddress(alice, "40547");

    mvc.perform(auth(get("/api/v1/delivery-slots").param("addressId", aliceAddress), bob))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    mvc.perform(
            auth(put("/api/v1/addresses/" + aliceAddress), bob)
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("street", "X", "houseNumber", "1", "postalCode", "40547", "city", "D"))))
        .andExpect(status().isNotFound());
    mvc.perform(auth(delete("/api/v1/addresses/" + aliceAddress), bob)).andExpect(status().isNotFound());
    mvc.perform(auth(get("/api/v1/addresses"), bob)).andExpect(jsonPath("$.length()").value(0));

    // Bob cannot reserve a slot for Alice's address.
    Object slotId = slots(alice, aliceAddress).getFirst().get("id");
    MvcResult reserve = reserve(bob, slotId, aliceAddress);
    org.assertj.core.api.Assertions.assertThat(reserve.getResponse().getStatus()).isEqualTo(404);

    // Alice's order is invisible to Bob.
    setQuantity(alice, productIdBySku("DK-4102"), 6);
    reserve(alice, slotId, aliceAddress);
    MvcResult placed = mvc.perform(auth(post("/api/v1/orders"), alice)).andExpect(status().isCreated()).andReturn();
    String orderId = read(placed, "$.id");
    mvc.perform(auth(get("/api/v1/orders/" + orderId), bob)).andExpect(status().isNotFound());
    mvc.perform(auth(post("/api/v1/orders/" + orderId + "/cancel"), bob)).andExpect(status().isNotFound());
    mvc.perform(auth(get("/api/v1/orders"), bob)).andExpect(jsonPath("$.length()").value(0));
    mvc.perform(auth(get("/api/v1/orders/" + UUID.randomUUID()), alice)).andExpect(status().isNotFound());
  }

  @Test
  void requestIdIsEchoedOrGenerated() throws Exception {
    mvc.perform(get("/api/v1/categories").header("X-Request-Id", "abc-123"))
        .andExpect(header().string("X-Request-Id", "abc-123"));
    mvc.perform(get("/api/v1/categories").header("X-Request-Id", "bad id with spaces"))
        .andExpect(header().string("X-Request-Id", org.hamcrest.Matchers.matchesPattern("[0-9a-f-]{36}")));
    mvc.perform(get("/api/v1/categories")).andExpect(header().exists("X-Request-Id"));
    mvc.perform(get("/api/v1/cart")).andExpect(header().exists("X-Request-Id")).andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
  }
}
