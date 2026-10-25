package de.dukkan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.support.IntegrationTest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** Offer prices per product, quantity tiers and schedules (merchant points 4–6; clock: Monday 10:00 Berlin). */
class PromotionsV2IntegrationTest extends IntegrationTest {

  private static Map<String, Object> money(long cents) {
    return Map.of("amountCents", cents, "currency", "EUR");
  }

  private Map<String, Object> promotion(String type) {
    OffsetDateTime from = OffsetDateTime.ofInstant(clock.instant().minus(Duration.ofHours(1)), ZoneOffset.UTC);
    Map<String, Object> body = new HashMap<>();
    body.put("title", Map.of("de", "Aktion"));
    body.put("type", type);
    body.put("validFrom", from.toString());
    body.put("validUntil", from.plusDays(14).toString());
    return body;
  }

  private MvcResult create(String merchant, UUID market, Map<String, Object> body, int expected) throws Exception {
    return mvc.perform(json(auth(post("/api/v1/merchant/markets/" + market + "/promotions"), merchant), toJson(body)))
        .andExpect(status().is(expected))
        .andReturn();
  }

  @Test
  void anOfferPricePerProduct() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Angebotspreis Markt");
    UUID ayran = addListing(merchant, market, "Ayran", 500);
    UUID tea = addListing(merchant, market, "Schwarzer Tee Rize", 800);

    Map<String, Object> fixed = promotion("FIXED_PRICE");
    fixed.put("offers", List.of(Map.of("productId", ayran, "offerPrice", money(200)), Map.of("productId", tea, "offerPrice", money(650))));
    MvcResult created = create(merchant, market, fixed, 201);
    assertThat(strings(created, "$.productIds")).containsExactlyInAnyOrder(ayran.toString(), tea.toString());
    assertThat(objects(created, "$.offers")).hasSize(2);

    mvc.perform(get("/api/v1/products/" + ayran))
        .andExpect(jsonPath("$.promotionalPrice.amountCents").value(200))
        .andExpect(jsonPath("$.promotion.type").value("FIXED_PRICE"))
        .andExpect(jsonPath("$.promotion.label").value("Angebot 2,00 €"));
    // Sorting by price uses the offer price.
    assertThat(strings(mvc.perform(get("/api/v1/markets/" + market + "/products").param("sort", "price_asc")).andReturn(), "$.items[*].id"))
        .containsExactly(ayran.toString(), tea.toString());

    String customer = registerUser();
    MvcResult cart = setQuantity(customer, market, ayran, 3);
    assertThat((Integer) read(cart, "$.subtotal.amountCents")).isEqualTo(1500);
    assertThat((Integer) read(cart, "$.discountTotal.amountCents")).isEqualTo(900);

    // An offer price must be a reduction.
    Map<String, Object> dearer = promotion("FIXED_PRICE");
    dearer.put("offers", List.of(Map.of("productId", ayran, "offerPrice", money(500))));
    mvc.perform(json(auth(post("/api/v1/merchant/markets/" + market + "/promotions"), merchant), toJson(dearer)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("BelowRegularPrice"));
  }

  @Test
  void quantityTiersTakeTheCheapestCombination() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Staffel Markt");
    UUID ayran = addListing(merchant, market, "Ayran", 200);

    Map<String, Object> tiers = promotion("MULTI_BUY");
    tiers.put("tiers", List.of(Map.of("quantity", 7, "price", money(1000)), Map.of("quantity", 3, "price", money(500))));
    tiers.put("productIds", List.of(ayran));
    MvcResult created = create(merchant, market, tiers, 201);
    assertThat(ints(created, "$.tiers[*].quantity")).containsExactly(3, 7);
    assertThat((Integer) read(created, "$.bundleQuantity")).isEqualTo(3);

    mvc.perform(get("/api/v1/products/" + ayran)).andExpect(jsonPath("$.promotion.label").value("3 für 5,00 € · 7 für 10,00 €"));

    String customer = registerUser();
    Map<Integer, Integer> expected = Map.of(3, 500, 6, 1000, 7, 1000, 8, 1200, 10, 1500);
    for (Map.Entry<Integer, Integer> e : expected.entrySet()) {
      MvcResult cart = setQuantity(customer, market, ayran, e.getKey());
      assertThat((Integer) read(cart, "$.items[0].lineTotal.amountCents")).as("%d units", e.getKey()).isEqualTo(e.getValue());
    }

    Map<String, Object> twice = promotion("MULTI_BUY");
    twice.put("tiers", List.of(Map.of("quantity", 3, "price", money(500)), Map.of("quantity", 3, "price", money(400))));
    twice.put("productIds", List.of(ayran));
    mvc.perform(json(auth(post("/api/v1/merchant/markets/" + market + "/promotions"), merchant), toJson(twice)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("DistinctQuantities"));
  }

  @Test
  void aScheduleLimitsTheDaysAndHours() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Zeitplan Markt");
    UUID ayran = addListing(merchant, market, "Ayran", 200);
    UUID tea = addListing(merchant, market, "Schwarzer Tee Rize", 800);

    // Ayran: Mondays and Tuesdays 08:00–12:00. Tea: weekends only.
    Map<String, Object> mornings = promotion("PERCENT_OFF");
    mornings.put("percentOff", 50);
    mornings.put("weekdays", List.of(2, 1));
    mornings.put("dailyFrom", "08:00");
    mornings.put("dailyUntil", "12:00");
    mornings.put("productIds", List.of(ayran));
    MvcResult created = create(merchant, market, mornings, 201);
    assertThat(ints(created, "$.weekdays")).containsExactly(1, 2);
    assertThat((String) read(created, "$.dailyFrom")).isEqualTo("08:00");
    Map<String, Object> weekend = promotion("PERCENT_OFF");
    weekend.put("percentOff", 10);
    weekend.put("weekdays", List.of(6, 7));
    weekend.put("productIds", List.of(tea));
    create(merchant, market, weekend, 201);

    // Monday 10:00: the morning offer runs, the weekend one does not.
    mvc.perform(get("/api/v1/products/" + ayran)).andExpect(jsonPath("$.promotionalPrice.amountCents").value(100));
    mvc.perform(get("/api/v1/products/" + tea)).andExpect(jsonPath("$.promotion").doesNotExist());
    assertThat(objects(mvc.perform(get("/api/v1/markets/" + market + "/promotions")).andReturn(), "$")).hasSize(1);

    // Monday 13:00: after the window.
    clock.advance(Duration.ofHours(3));
    mvc.perform(get("/api/v1/products/" + ayran)).andExpect(jsonPath("$.promotion").doesNotExist());
    // Wednesday 09:00: wrong day.
    clock.advance(Duration.ofHours(44));
    mvc.perform(get("/api/v1/products/" + ayran)).andExpect(jsonPath("$.promotion").doesNotExist());
    // Saturday 09:00: the weekend offer.
    clock.advance(Duration.ofDays(3));
    mvc.perform(get("/api/v1/products/" + tea)).andExpect(jsonPath("$.promotionalPrice.amountCents").value(720));
    // The merchant still sees both.
    assertThat(objects(mvc.perform(auth(get("/api/v1/merchant/markets/" + market + "/promotions"), merchant)).andReturn(), "$")).hasSize(2);

    Map<String, Object> backwards = promotion("PERCENT_OFF");
    backwards.put("percentOff", 10);
    backwards.put("dailyFrom", "12:00");
    backwards.put("dailyUntil", "08:00");
    backwards.put("productIds", List.of(tea));
    mvc.perform(json(auth(post("/api/v1/merchant/markets/" + market + "/promotions"), merchant), toJson(backwards)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("DailyWindow"));
  }
}
