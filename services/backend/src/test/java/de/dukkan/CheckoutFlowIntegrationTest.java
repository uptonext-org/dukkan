package de.dukkan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.support.IntegrationTest;
import de.dukkan.support.OpenApiContract;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** End-to-end customer journey against a real PostgreSQL database. */
class CheckoutFlowIntegrationTest extends IntegrationTest {

  private final OpenApiContract contract = OpenApiContract.load();

  private void assertMatchesSchema(MvcResult result, String path, String schema) throws Exception {
    Map<String, Object> body = read(result, path);
    assertThat(body).as("%s at %s", schema, path).containsKeys(contract.requiredFields(schema).toArray(String[]::new));
  }

  @Test
  void registerLoginAndMe() throws Exception {
    String email = "Mixed.Case." + uniqueEmail();
    MvcResult registered =
        mvc.perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        toJson(
                            Map.of(
                                "email", email,
                                "password", "correct-horse-battery",
                                "firstName", "Anna",
                                "lastName", "Muster"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(3600))
            .andExpect(jsonPath("$.user.email").value(email.toLowerCase(java.util.Locale.ROOT)))
            .andReturn();
    assertMatchesSchema(registered, "$", "AuthResponse");

    MvcResult login =
        mvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(Map.of("email", email.toUpperCase(java.util.Locale.ROOT), "password", "correct-horse-battery"))))
            .andExpect(status().isOk())
            .andReturn();
    String token = read(login, "$.accessToken");

    mvc.perform(auth(get("/api/v1/me"), token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.firstName").value("Anna"))
        .andExpect(jsonPath("$.email").value(email.toLowerCase(java.util.Locale.ROOT)));
  }

  @Test
  void browseSearchAndPromotions() throws Exception {
    MvcResult categories =
        mvc.perform(get("/api/v1/categories")).andExpect(status().isOk()).andReturn();
    List<Integer> sortOrders = read(categories, "$[*].sortOrder");
    assertThat(sortOrders).hasSizeGreaterThanOrEqualTo(8).isSorted();
    List<String> parents = read(categories, "$[?(@.parentId != null)].parentId");
    assertThat(parents).isNotEmpty();
    List<String> fruitAndVegIds = read(categories, "$[?(@.slug == 'fruit-vegetables')].id");
    String fruitAndVeg = fruitAndVegIds.getFirst();

    // Parent category includes products of its sub-categories.
    MvcResult page =
        mvc.perform(get("/api/v1/products").param("categoryId", fruitAndVeg).param("size", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(5)))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(5))
            .andExpect(jsonPath("$.totalItems").value(13))
            .andExpect(jsonPath("$.totalPages").value(3))
            .andReturn();
    assertMatchesSchema(page, "$", "ProductPage");
    assertMatchesSchema(page, "$.items[0]", "ProductSummary");

    MvcResult byPrice =
        mvc.perform(get("/api/v1/products").param("sort", "price_asc").param("size", "100")).andReturn();
    List<Integer> prices = read(byPrice, "$.items[*].price.amountCents");
    assertThat(prices).hasSizeGreaterThanOrEqualTo(40).isSorted();

    mvc.perform(get("/api/v1/products").param("q", "PASTA"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[*].name", hasItem("Spaghetti")))
        .andExpect(jsonPath("$.items[*].name", hasItem("Penne Rigate")));
    // Brand search.
    mvc.perform(get("/api/v1/products").param("q", "weidenhof"))
        .andExpect(jsonPath("$.items[*].brand", everyItem(org.hamcrest.Matchers.is("Weidenhof"))));
    // LIKE wildcards are matched literally: "%" only finds names containing a percent sign.
    mvc.perform(get("/api/v1/products").param("q", "%"))
        .andExpect(jsonPath("$.totalItems").value(2))
        .andExpect(jsonPath("$.items[*].name", hasItem("Fresh Whole Milk 3.5%")));
    mvc.perform(get("/api/v1/products").param("q", "_")).andExpect(jsonPath("$.totalItems").value(0));

    mvc.perform(get("/api/v1/products").param("promotedOnly", "true").param("size", "100"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalItems").value(11))
        .andExpect(jsonPath("$.items[*].promotion", everyItem(notNullValue())));

    // Prefix matches first (shortest first), then other matches such as "Baby Spinach".
    MvcResult suggestions =
        mvc.perform(get("/api/v1/search/suggestions").param("q", "sp").param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0]").value("Sponges"))
            .andExpect(jsonPath("$[1]").value("Spaghetti"))
            .andExpect(jsonPath("$[*]", hasItem("Baby Spinach")))
            .andReturn();
    List<String> names = read(suggestions, "$");
    assertThat(names.indexOf("Baby Spinach")).isGreaterThan(names.indexOf("Sparkling Mineral Water"));
    assertThat(names).hasSize(4);
    mvc.perform(get("/api/v1/search/suggestions").param("q", "sp").param("limit", "2"))
        .andExpect(jsonPath("$", hasSize(2)));

    String strawberries = productIdBySku("DK-1003");
    MvcResult detail =
        mvc.perform(get("/api/v1/products/" + strawberries))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.maxOrderQuantity").value(24))
            .andExpect(jsonPath("$.price.amountCents").value(349))
            .andExpect(jsonPath("$.price.currency").value("EUR"))
            .andExpect(jsonPath("$.comparisonPrice.price.amountCents").value(698))
            .andExpect(jsonPath("$.comparisonPrice.unit").value("kg"))
            // Two promotions overlap on strawberries; the most recently created (-30%) wins.
            .andExpect(jsonPath("$.promotion.label").value("-30%"))
            .andExpect(jsonPath("$.promotion.type").value("PERCENT_OFF"))
            .andExpect(jsonPath("$.imageUrl").value(nullValue()))
            .andReturn();
    assertMatchesSchema(detail, "$", "ProductDetail");

    MvcResult promotions =
        mvc.perform(get("/api/v1/promotions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(5)))
            .andExpect(jsonPath("$[*].title", org.hamcrest.Matchers.not(hasItem("Expired winter deal"))))
            .andReturn();
    assertMatchesSchema(promotions, "$[0]", "Promotion");
    mvc.perform(get("/api/v1/promotions"))
        .andExpect(jsonPath("$[?(@.type == 'MULTI_BUY')].bundlePrice.amountCents", hasItem(300)));
  }

  @Test
  void fullCheckoutFlow() throws Exception {
    String token = registerUser();

    // Cart: 3 x Spaghetti (3 for 3.00), 2 x Strawberries (-30%), 5 x Olive oil (no promo).
    String spaghetti = productIdBySku("DK-4001");
    String strawberries = productIdBySku("DK-1003");
    String oliveOil = productIdBySku("DK-4102");
    assertThat(setQuantity(token, spaghetti, 3).getResponse().getStatus()).isEqualTo(200);
    assertThat(setQuantity(token, strawberries, 2).getResponse().getStatus()).isEqualTo(200);
    MvcResult cart = setQuantity(token, oliveOil, 5);
    assertThat(cart.getResponse().getStatus()).isEqualTo(200);
    assertMatchesSchema(cart, "$", "Cart");
    assertMatchesSchema(cart, "$.items[0]", "CartItem");
    assertThat((Integer) read(cart, "$.itemCount")).isEqualTo(10);
    assertThat((Integer) read(cart, "$.subtotal.amountCents")).isEqualTo(387 + 698 + 3495);
    // Spaghetti 387 -> 300 (87 off); strawberries round_half_up(349 * 0.3) = 105 per unit -> 210 off.
    assertThat((Integer) read(cart, "$.discountTotal.amountCents")).isEqualTo(87 + 210);
    assertThat((Integer) read(cart, "$.deliveryFee.amountCents")).isZero();
    assertThat((Integer) read(cart, "$.total.amountCents")).isEqualTo(4580 - 297);
    assertThat((Boolean) read(cart, "$.meetsMinimumOrderValue")).isTrue();
    assertThat((Integer) read(cart, "$.minimumOrderValue.amountCents")).isEqualTo(3500);
    Object delivery = read(cart, "$.delivery");
    assertThat(delivery).isNull();

    // Setting the same quantity again is idempotent.
    MvcResult again = setQuantity(token, spaghetti, 3);
    assertThat((Integer) read(again, "$.itemCount")).isEqualTo(10);

    // Non-serviceable address: created, but no slots.
    String farAway = createAddress(token, "12345");
    mvc.perform(auth(get("/api/v1/addresses"), token))
        .andExpect(jsonPath("$[0].isDefault").value(true))
        .andExpect(jsonPath("$[0].serviceable").value(false))
        .andExpect(jsonPath("$[0].countryCode").value("DE"));
    mvc.perform(auth(get("/api/v1/delivery-slots").param("addressId", farAway), token))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("ADDRESS_NOT_SERVICEABLE"));
    mvc.perform(get("/api/v1/delivery-areas/check").param("postalCode", "12345"))
        .andExpect(jsonPath("$.serviceable").value(false))
        .andExpect(jsonPath("$.hubName").value(nullValue()));

    // Serviceable address.
    mvc.perform(get("/api/v1/delivery-areas/check").param("postalCode", "41460"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.serviceable").value(true))
        .andExpect(jsonPath("$.hubName").value("Hub Rhein-West"));
    String home = createAddress(token, "41460");
    MvcResult addresses = mvc.perform(auth(get("/api/v1/addresses"), token)).andReturn();
    List<Object> addressList = read(addresses, "$");
    assertThat(addressList).hasSize(2);
    assertMatchesSchema(addresses, "$[0]", "Address");

    List<Map<String, Object>> slots = slots(token, home);
    assertThat(slots).hasSize(6 * 13);
    assertThat(slots(token, home)).hasSize(6 * 13); // generation is idempotent
    Map<String, Object> first = slots.getFirst();
    assertThat(first.get("startsAt")).isEqualTo("2026-10-20T08:00:00+02:00");
    assertThat(first.get("orderCutoffAt")).isEqualTo("2026-10-19T22:00:00+02:00");
    assertThat(first.get("available")).isEqualTo(true);
    assertThat(first.get("remainingCapacity")).isEqualTo(8);
    Object slotId = first.get("id");

    // Reserving with the non-serviceable address is rejected.
    MvcResult rejected = reserve(token, slotId, farAway);
    assertThat(rejected.getResponse().getStatus()).isEqualTo(422);
    assertThat((String) read(rejected, "$.code")).isEqualTo("ADDRESS_NOT_SERVICEABLE");

    MvcResult reserved = reserve(token, slotId, home);
    assertThat(reserved.getResponse().getStatus()).isEqualTo(200);
    assertMatchesSchema(reserved, "$.delivery", "DeliveryReservation");
    assertThat((String) read(reserved, "$.delivery.addressId")).isEqualTo(home);
    assertThat((Integer) read(reserved, "$.delivery.slot.remainingCapacity")).isEqualTo(7);
    assertThat((String) read(reserved, "$.delivery.expiresAt")).isEqualTo("2026-10-19T22:00:00+02:00");
    assertThat((Integer) read(reserved, "$.deliveryFee.amountCents")).isEqualTo(99);
    assertThat((Integer) read(reserved, "$.total.amountCents")).isEqualTo(4283 + 99);
    // Reserving the same slot again does not consume more capacity.
    MvcResult reservedAgain = reserve(token, slotId, home);
    assertThat((Integer) read(reservedAgain, "$.delivery.slot.remainingCapacity")).isEqualTo(7);

    // Place the order (idempotent with Idempotency-Key).
    MvcResult placed =
        mvc.perform(auth(post("/api/v1/orders"), token).header("Idempotency-Key", "checkout-1"))
            .andExpect(status().isCreated())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.orderNumber", matchesPattern("DK-20261019-\\d{6}")))
            .andExpect(jsonPath("$.status").value("PLACED"))
            .andExpect(jsonPath("$.items", hasSize(3)))
            .andExpect(jsonPath("$.subtotal.amountCents").value(4580))
            .andExpect(jsonPath("$.discountTotal.amountCents").value(297))
            .andExpect(jsonPath("$.deliveryFee.amountCents").value(99))
            .andExpect(jsonPath("$.total.amountCents").value(4382))
            .andExpect(jsonPath("$.address.id").value(home))
            .andExpect(jsonPath("$.address.postalCode").value("41460"))
            .andExpect(jsonPath("$.slot.id").value(slotId))
            .andExpect(jsonPath("$.slot.remainingCapacity").value(7))
            .andExpect(jsonPath("$.placedAt").value("2026-10-19T10:00:00+02:00"))
            .andReturn();
    assertMatchesSchema(placed, "$", "Order");
    assertMatchesSchema(placed, "$.items[0]", "OrderItem");
    String orderId = read(placed, "$.id");

    mvc.perform(auth(post("/api/v1/orders"), token).header("Idempotency-Key", "checkout-1"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(orderId));

    // The cart is empty and the reservation became the order's.
    mvc.perform(auth(get("/api/v1/cart"), token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items", hasSize(0)))
        .andExpect(jsonPath("$.delivery").value(nullValue()))
        .andExpect(jsonPath("$.total.amountCents").value(0));
    assertThat(slots(token, home).getFirst().get("remainingCapacity")).isEqualTo(7);

    // Price changes after placement do not affect the order (snapshot): order items keep prices.
    mvc.perform(auth(get("/api/v1/orders"), token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].id").value(orderId));
    mvc.perform(auth(get("/api/v1/orders/" + orderId), token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[?(@.name == 'Spaghetti')].lineTotal.amountCents", hasItem(300)));

    // Cancel before cutoff releases capacity; a second cancel is rejected.
    mvc.perform(auth(post("/api/v1/orders/" + orderId + "/cancel"), token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
    assertThat(slots(token, home).getFirst().get("remainingCapacity")).isEqualTo(8);
    mvc.perform(auth(post("/api/v1/orders/" + orderId + "/cancel"), token))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("ORDER_NOT_CANCELLABLE"));

    // Address management.
    mvc.perform(
            auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/addresses/" + farAway), token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    toJson(
                        Map.of(
                            "street", "Neue Strasse",
                            "houseNumber", "2",
                            "postalCode", "40545",
                            "city", "Düsseldorf",
                            "isDefault", true))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.serviceable").value(true))
        .andExpect(jsonPath("$.isDefault").value(true));
    mvc.perform(auth(get("/api/v1/addresses"), token))
        .andExpect(jsonPath("$[0].id").value(farAway))
        .andExpect(jsonPath("$[?(@.isDefault == true)]", hasSize(1)));
    mvc.perform(auth(delete("/api/v1/addresses/" + farAway), token)).andExpect(status().isNoContent());
    mvc.perform(auth(get("/api/v1/addresses"), token)).andExpect(jsonPath("$", hasSize(1)));
  }

  @Test
  void cancellationAfterCutoffIsRejected() throws Exception {
    String email = uniqueEmail();
    String token = registerUser(email);
    String address = createAddress(token, "41462");
    setQuantity(token, productIdBySku("DK-4102"), 6);
    Object slotId = slots(token, address).get(14).get("id"); // a Wednesday slot
    assertThat(reserve(token, slotId, address).getResponse().getStatus()).isEqualTo(200);
    MvcResult placed = mvc.perform(auth(post("/api/v1/orders"), token)).andExpect(status().isCreated()).andReturn();
    String orderId = read(placed, "$.id");

    clock.advance(Duration.ofDays(2)); // Wednesday 10:00, past Tuesday 22:00 cutoff
    token = login(email); // the first token has expired by now

    mvc.perform(auth(post("/api/v1/orders/" + orderId + "/cancel"), token))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("ORDER_NOT_CANCELLABLE"));
  }

  @Test
  void clearingTheCartReleasesTheReservation() throws Exception {
    String token = registerUser();
    String address = createAddress(token, "41464");
    setQuantity(token, productIdBySku("DK-1101"), 1);
    Object slotId = slots(token, address).get(20).get("id");
    reserve(token, slotId, address);
    Object otherSlot = slots(token, address).get(21).get("id");

    // Replacing the reservation releases the old slot.
    MvcResult replaced = reserve(token, otherSlot, address);
    assertThat((String) read(replaced, "$.delivery.slot.id")).isEqualTo(otherSlot);
    assertThat(slots(token, address).get(20).get("remainingCapacity")).isEqualTo(8);
    assertThat(slots(token, address).get(21).get("remainingCapacity")).isEqualTo(7);

    mvc.perform(auth(delete("/api/v1/cart"), token)).andExpect(status().isNoContent());
    mvc.perform(auth(get("/api/v1/cart"), token))
        .andExpect(jsonPath("$.items", hasSize(0)))
        .andExpect(jsonPath("$.delivery").value(nullValue()));
    assertThat(slots(token, address).get(21).get("remainingCapacity")).isEqualTo(8);
  }
}
