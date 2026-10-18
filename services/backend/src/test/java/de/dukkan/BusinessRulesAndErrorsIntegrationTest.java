package de.dukkan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.support.IntegrationTest;
import de.dukkan.support.OpenApiContract;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class BusinessRulesAndErrorsIntegrationTest extends IntegrationTest {

  private final OpenApiContract contract = OpenApiContract.load();

  private void assertProblem(MvcResult result, int status, String code) throws Exception {
    assertThat(result.getResponse().getStatus()).isEqualTo(status);
    assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    Map<String, Object> body = read(result, "$");
    assertThat(body).containsKeys(contract.requiredFields("Problem").toArray(String[]::new));
    assertThat(body).containsEntry("code", code).containsEntry("status", status);
    assertThat(result.getResponse().getContentAsString()).doesNotContain("Exception").doesNotContain("at de.dukkan");
  }

  @Test
  void validationErrorsListTheOffendingFields() throws Exception {
    MvcResult result =
        mvc.perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(Map.of("email", "nope", "password", "short", "firstName", "", "lastName", "B"))))
            .andExpect(jsonPath("$.errors[*].field", hasItem("email")))
            .andExpect(jsonPath("$.errors[*].field", hasItem("password")))
            .andExpect(jsonPath("$.errors[*].field", hasItem("firstName")))
            .andExpect(jsonPath("$.errors[0].message").exists())
            .andReturn();
    assertProblem(result, 400, "VALIDATION_FAILED");
  }

  @Test
  void queryParameterValidation() throws Exception {
    MvcResult size = mvc.perform(get("/api/v1/products").param("size", "0")).andReturn();
    assertProblem(size, 400, "VALIDATION_FAILED");
    assertThat((String) read(size, "$.errors[0].field")).isEqualTo("size");

    assertProblem(mvc.perform(get("/api/v1/products").param("size", "101")).andReturn(), 400, "VALIDATION_FAILED");
    assertProblem(mvc.perform(get("/api/v1/products").param("page", "-1")).andReturn(), 400, "VALIDATION_FAILED");
    assertProblem(mvc.perform(get("/api/v1/products").param("sort", "cheapest")).andReturn(), 400, "VALIDATION_FAILED");
    assertProblem(mvc.perform(get("/api/v1/products").param("q", "x".repeat(101))).andReturn(), 400, "VALIDATION_FAILED");
    assertProblem(
        mvc.perform(get("/api/v1/products").param("categoryId", "not-a-uuid")).andReturn(), 400, "VALIDATION_FAILED");
    assertProblem(mvc.perform(get("/api/v1/search/suggestions")).andReturn(), 400, "VALIDATION_FAILED");
    assertProblem(mvc.perform(get("/api/v1/search/suggestions").param("q", "a")).andReturn(), 400, "VALIDATION_FAILED");
    assertProblem(
        mvc.perform(get("/api/v1/search/suggestions").param("q", "ap").param("limit", "11")).andReturn(),
        400,
        "VALIDATION_FAILED");
    assertProblem(
        mvc.perform(get("/api/v1/delivery-areas/check").param("postalCode", "4146")).andReturn(),
        400,
        "VALIDATION_FAILED");
  }

  @Test
  void notFoundAndMalformedRequests() throws Exception {
    assertProblem(mvc.perform(get("/api/v1/products/" + UUID.randomUUID())).andReturn(), 404, "NOT_FOUND");
    assertProblem(mvc.perform(get("/api/v1/products/not-a-uuid")).andReturn(), 400, "VALIDATION_FAILED");
    // Unknown paths are not public: anonymous callers get 401, authenticated ones 404.
    assertProblem(mvc.perform(get("/api/v1/does-not-exist")).andReturn(), 401, "UNAUTHORIZED");
    assertProblem(mvc.perform(auth(get("/api/v1/does-not-exist"), registerUser())).andReturn(), 404, "NOT_FOUND");
    assertProblem(
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{not json")).andReturn(),
        400,
        "VALIDATION_FAILED");
    assertProblem(mvc.perform(auth(post("/api/v1/categories"), registerUser())).andReturn(), 405, "METHOD_NOT_ALLOWED");
  }

  @Test
  void cartQuantityRules() throws Exception {
    String token = registerUser();
    String spaghetti = productIdBySku("DK-4001");

    assertProblem(setQuantity(token, productIdBySku("DK-1006"), 1), 422, "PRODUCT_UNAVAILABLE");
    assertProblem(setQuantity(token, spaghetti, 25), 422, "MAX_QUANTITY_EXCEEDED");
    assertProblem(setQuantity(token, spaghetti, 100), 400, "VALIDATION_FAILED");
    assertProblem(setQuantity(token, spaghetti, -1), 400, "VALIDATION_FAILED");
    assertProblem(setQuantity(token, UUID.randomUUID().toString(), 1), 404, "NOT_FOUND");
    assertProblem(
        mvc.perform(auth(put("/api/v1/cart/items/" + spaghetti), token).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andReturn(),
        400,
        "VALIDATION_FAILED");

    assertThat(setQuantity(token, spaghetti, 24).getResponse().getStatus()).isEqualTo(200);
    MvcResult removed = setQuantity(token, spaghetti, 0);
    assertThat((Integer) read(removed, "$.itemCount")).isZero();
    mvc.perform(auth(delete("/api/v1/cart/items/" + spaghetti), token))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
  }

  @Test
  void checkoutPreconditions() throws Exception {
    String email = uniqueEmail();
    String token = registerUser(email);
    String address = createAddress(token, "40549");

    assertProblem(mvc.perform(auth(post("/api/v1/orders"), token)).andReturn(), 422, "CART_EMPTY");

    setQuantity(token, productIdBySku("DK-1101"), 2); // 2 x 1.29 = 2.58 < 35.00
    assertProblem(mvc.perform(auth(post("/api/v1/orders"), token)).andReturn(), 422, "SLOT_NOT_RESERVED");

    Object slotId = slots(token, address).getFirst().get("id");
    MvcResult reserved = reserve(token, slotId, address);
    assertThat((Boolean) read(reserved, "$.meetsMinimumOrderValue")).isFalse();
    assertProblem(mvc.perform(auth(post("/api/v1/orders"), token)).andReturn(), 422, "MINIMUM_ORDER_NOT_MET");

    // Minimum is measured after discounts: 12 x 2.99 water crates = 35.88, but 2-for-5.00 makes it 30.00.
    setQuantity(token, productIdBySku("DK-1101"), 0);
    MvcResult water = setQuantity(token, productIdBySku("DK-5001"), 12);
    assertThat((Integer) read(water, "$.subtotal.amountCents")).isEqualTo(3588);
    assertThat((Integer) read(water, "$.discountTotal.amountCents")).isEqualTo(588);
    assertThat((Boolean) read(water, "$.meetsMinimumOrderValue")).isFalse();
    assertProblem(mvc.perform(auth(post("/api/v1/orders"), token)).andReturn(), 422, "MINIMUM_ORDER_NOT_MET");

    setQuantity(token, productIdBySku("DK-4102"), 1);
    clock.advance(Duration.ofHours(13)); // Monday 23:00, past the 22:00 cutoff for Tuesday's slot
    token = login(email); // the first token has expired by now
    assertProblem(mvc.perform(auth(post("/api/v1/orders"), token)).andReturn(), 422, "SLOT_CUTOFF_PASSED");

    // The expired reservation is released when the cart is read; reserving a past-cutoff slot fails.
    mvc.perform(auth(get("/api/v1/cart"), token)).andExpect(jsonPath("$.delivery").value(nullValue()));
    assertProblem(reserve(token, slotId, address), 422, "SLOT_CUTOFF_PASSED");
    assertThat(slots(token, address).stream().filter(s -> s.get("id").equals(slotId)).findFirst().orElseThrow())
        .containsEntry("available", false);
  }

  @Test
  void reservationErrors() throws Exception {
    String token = registerUser();
    String address = createAddress(token, "40545");
    assertProblem(reserve(token, UUID.randomUUID(), address), 404, "NOT_FOUND");
    assertProblem(reserve(token, UUID.randomUUID(), UUID.randomUUID().toString()), 404, "NOT_FOUND");
    assertProblem(
        mvc.perform(auth(put("/api/v1/cart/delivery"), token).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andReturn(),
        400,
        "VALIDATION_FAILED");

    // A slot of another hub is not offered for this address.
    String otherHubAddress = createAddress(token, "50668");
    Object otherHubSlot = slots(token, otherHubAddress).getLast().get("id");
    assertProblem(reserve(token, otherHubSlot, address), 404, "NOT_FOUND");
  }
}
