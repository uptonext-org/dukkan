package de.dukkan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** Problem responses and the 422 business-rule codes of carts, slots and checkout. */
class BusinessRulesIntegrationTest extends IntegrationTest {

  private static void expectProblem(MvcResult result, int status, String code) throws Exception {
    assertThat(result.getResponse().getStatus()).isEqualTo(status);
    assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat((String) read(result, "$.code")).isEqualTo(code);
    assertThat((String) read(result, "$.type")).isNotBlank();
    assertThat((String) read(result, "$.title")).isNotBlank();
    assertThat((Integer) read(result, "$.status")).isEqualTo(status);
  }

  @Test
  void validationErrorsListFieldsWithConstraintCodes() throws Exception {
    String token = registerUser();
    mvc.perform(json(auth(post("/api/v1/addresses"), token), toJson(Map.of("street", "", "houseNumber", "1", "postalCode", "2009", "city", "Hamburg"))))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[?(@.field == 'street')].code").value(org.hamcrest.Matchers.containsInAnyOrder("NotBlank", "Size")))
        .andExpect(jsonPath("$.errors[?(@.field == 'postalCode')].code").value("Pattern"))
        .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
    mvc.perform(get("/api/v1/markets").param("lat", "53.5"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("lng"))
        .andExpect(jsonPath("$.errors[0].code").value("NotNull"));
    mvc.perform(get("/api/v1/markets").param("lat", "53.5").param("lng", "10").param("radiusKm", "51"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("DecimalMax"));
    mvc.perform(json(auth(put("/api/v1/markets/" + marketId(ANATOLIA) + "/cart/items/" + UUID.randomUUID()), token), "{\"quantity\":100}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("quantity"))
        .andExpect(jsonPath("$.errors[0].code").value("Max"));
    mvc.perform(json(auth(post("/api/v1/addresses"), token), "{not json"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("body"));
  }

  @Test
  void cartRules() throws Exception {
    String token = registerUser();
    UUID anatolia = marketId(ANATOLIA);

    expectProblem(setQuantity(token, anatolia, UUID.randomUUID(), 1), 404, "NOT_FOUND");
    expectProblem(setQuantity(token, anatolia, listingId(ANATOLIA, "Sumach"), 1), 422, "PRODUCT_UNAVAILABLE");
    UUID lamb = listingId(ANATOLIA, "Lammkoteletts");
    expectProblem(setQuantity(token, anatolia, lamb, 13), 422, "MAX_QUANTITY_EXCEEDED");
    assertThat(setQuantity(token, anatolia, lamb, 12).getResponse().getStatus()).isEqualTo(200);
    MvcResult removed = mvc.perform(auth(delete("/api/v1/markets/" + anatolia + "/cart/items/" + lamb), token)).andReturn();
    assertThat(objects(removed, "$.items")).isEmpty();

    mvc.perform(auth(get("/api/v1/markets/" + UUID.randomUUID() + "/cart"), token)).andExpect(status().isNotFound());
    mvc.perform(auth(delete("/api/v1/markets/" + anatolia + "/cart"), token)).andExpect(status().isNoContent());
  }

  @Test
  void checkoutRules() throws Exception {
    String token = registerUser();
    UUID anatolia = marketId(ANATOLIA);

    expectProblem(placeOrder(token, anatolia), 422, "CART_EMPTY");
    UUID cucumber = listingId(ANATOLIA, "Salatgurke");
    setQuantity(token, anatolia, cucumber, 1);
    expectProblem(placeOrder(token, anatolia), 422, "SLOT_NOT_RESERVED");

    List<Map<String, Object>> pickup = slots(null, anatolia, "PICKUP", null);
    // Today's first pickup slot (11:00, cutoff 10:00) is already past its cutoff at 10:00.
    Map<String, Object> passed = pickup.getFirst();
    assertThat(passed.get("available")).isEqualTo(false);
    expectProblem(reserve(token, anatolia, "PICKUP", (String) passed.get("id"), null), 422, "SLOT_CUTOFF_PASSED");

    String slot = availableSlot(pickup, 1);
    assertThat(reserve(token, anatolia, "PICKUP", slot, null).getResponse().getStatus()).isEqualTo(200);
    expectProblem(placeOrder(token, anatolia), 422, "MINIMUM_ORDER_NOT_MET");

    expectProblem(reserve(token, anatolia, "DELIVERY", slot, null), 422, "ADDRESS_REQUIRED");
    String home = createAddress(token, "Pfefferminzgang", "8", "20099");
    expectProblem(reserve(token, anatolia, "DELIVERY", slot, home), 422, "SLOT_TYPE_MISMATCH");
    expectProblem(reserve(token, anatolia, "PICKUP", UUID.randomUUID().toString(), null), 404, "NOT_FOUND");
    String bakerySlot = availableSlot(slots(null, marketId(BAKERY), "PICKUP", null), 1);
    expectProblem(reserve(token, anatolia, "PICKUP", bakerySlot, null), 404, "NOT_FOUND");

    // The reservation expires at the cutoff.
    MvcResult cart = mvc.perform(auth(get("/api/v1/markets/" + anatolia + "/cart"), token)).andReturn();
    java.time.Instant cutoff = java.time.OffsetDateTime.parse(read(cart, "$.fulfilment.expiresAt")).toInstant();
    setQuantity(token, anatolia, cucumber, 24);
    clock.set(cutoff);
    expectProblem(placeOrder(token, anatolia), 422, "SLOT_CUTOFF_PASSED");
    MvcResult expired = mvc.perform(auth(get("/api/v1/markets/" + anatolia + "/cart"), token)).andReturn();
    assertThat(object(expired, "$.fulfilment")).isNull();
  }

  @Test
  void deliveryOutsideTheRadiusIsRejected() throws Exception {
    String token = registerUser();
    UUID anatolia = marketId(ANATOLIA);
    // Harburg is ~11.6 km from Anatolia (radius 4 km).
    String harburg = createAddress(token, "Lindenblütenweg", "3", "21075");
    MvcResult slots = mvc.perform(auth(get("/api/v1/markets/" + anatolia + "/slots").param("type", "DELIVERY").param("addressId", harburg), token)).andReturn();
    expectProblem(slots, 422, "ADDRESS_OUTSIDE_DELIVERY_AREA");

    String slot = availableSlot(slots(null, anatolia, "DELIVERY", null), 1);
    setQuantity(token, anatolia, listingId(ANATOLIA, "Salatgurke"), 1);
    expectProblem(reserve(token, anatolia, "DELIVERY", slot, harburg), 422, "ADDRESS_OUTSIDE_DELIVERY_AREA");

    // An address that could not be geocoded cannot be checked either.
    String unknown = createAddress(token, "Marienplatz", "1", "80331");
    expectProblem(reserve(token, anatolia, "DELIVERY", slot, unknown), 422, "ADDRESS_OUTSIDE_DELIVERY_AREA");

    // addressId on the public slot list needs a token; another user's address is not found.
    mvc.perform(get("/api/v1/markets/" + anatolia + "/slots").param("type", "DELIVERY").param("addressId", harburg))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    String other = registerUser();
    mvc.perform(auth(get("/api/v1/markets/" + anatolia + "/slots").param("type", "DELIVERY").param("addressId", harburg), other))
        .andExpect(status().isNotFound());
    // Pickup-only markets offer no delivery slots.
    mvc.perform(get("/api/v1/markets/" + marketId(BAKERY) + "/slots").param("type", "DELIVERY"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("FULFILMENT_NOT_OFFERED"));
  }

  @Test
  void slotsHaveTypeCapacityAndCutoff() throws Exception {
    UUID anatolia = marketId(ANATOLIA);
    List<Map<String, Object>> delivery = slots(null, anatolia, "DELIVERY", null);
    List<Map<String, Object>> pickup = slots(null, anatolia, "PICKUP", null);
    assertThat(delivery).isNotEmpty().allSatisfy(s -> assertThat(s.get("type")).isEqualTo("DELIVERY"));
    assertThat(pickup).allSatisfy(s -> assertThat(s.get("type")).isEqualTo("PICKUP"));
    Map<String, Object> d = delivery.get(delivery.size() - 1);
    Map<String, Object> p = pickup.get(pickup.size() - 1);
    assertThat(d.get("remainingCapacity")).isEqualTo(6);
    assertThat(p.get("remainingCapacity")).isEqualTo(10);
    assertThat(java.time.Duration.between(
            java.time.OffsetDateTime.parse((String) d.get("orderCutoffAt")), java.time.OffsetDateTime.parse((String) d.get("startsAt"))))
        .isEqualTo(java.time.Duration.ofHours(2));
    assertThat(java.time.Duration.between(
            java.time.OffsetDateTime.parse((String) p.get("orderCutoffAt")), java.time.OffsetDateTime.parse((String) p.get("startsAt"))))
        .isEqualTo(java.time.Duration.ofHours(1));
    // Listing slots again is idempotent.
    assertThat(slots(null, anatolia, "DELIVERY", null)).hasSameSizeAs(delivery);
  }
}
