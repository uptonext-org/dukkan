package de.dukkan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

class MerchantIntegrationTest extends IntegrationTest {

  @Test
  void merchantCreatesAndUpdatesAMarket() throws Exception {
    String merchant = registerMerchant();
    MvcResult created =
        mvc.perform(json(auth(post("/api/v1/merchant/markets"), merchant), toJson(marketProfile("Gemüse Özdemir Test"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.slug").value("gemuse-ozdemir-test"))
            .andExpect(jsonPath("$.type.code").value("GROCERY"))
            .andExpect(jsonPath("$.openingHours.length()").value(6))
            .andExpect(jsonPath("$.imprint.legalName").value("Gemüse Özdemir Test GmbH"))
            .andExpect(jsonPath("$.fulfilment.deliveryRadiusKm").value(3.0))
            .andReturn();
    String id = read(created, "$.id");
    // Same name again → a different slug.
    mvc.perform(json(auth(post("/api/v1/merchant/markets"), merchant), toJson(marketProfile("Gemüse Özdemir Test"))))
        .andExpect(jsonPath("$.slug").value("gemuse-ozdemir-test-2"));

    Map<String, Object> update = marketProfile("Gemüse Özdemir");
    update.put("typeCode", "GREENGROCER");
    update.put("description", Map.of("de", "Frisches Gemüse", "ar", "خضار طازجة"));
    mvc.perform(json(auth(put("/api/v1/merchant/markets/" + id), merchant), toJson(update)).header(HttpHeaders.ACCEPT_LANGUAGE, "ar"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Gemüse Özdemir"))
        .andExpect(jsonPath("$.type.code").value("GREENGROCER"))
        .andExpect(jsonPath("$.description").value("خضار طازجة"));

    MvcResult mine = mvc.perform(auth(get("/api/v1/merchant/markets"), merchant)).andExpect(status().isOk()).andReturn();
    assertThat(strings(mine, "$[*].id")).contains(id);
    assertThat(strings(mine, "$[*].status")).containsOnly("ACTIVE");
  }

  @Test
  void invalidProfilesAreRejectedWithFieldErrors() throws Exception {
    String merchant = registerMerchant();
    Map<String, Object> unknownType = marketProfile("Testmarkt");
    unknownType.put("typeCode", "SPACESHIP");
    mvc.perform(json(auth(post("/api/v1/merchant/markets"), merchant), toJson(unknownType)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("typeCode"))
        .andExpect(jsonPath("$.errors[0].code").value("UnknownCode"));

    Map<String, Object> noHours = marketProfile("Testmarkt");
    noHours.put("openingHours", List.of());
    noHours.put("name", "X");
    mvc.perform(json(auth(post("/api/v1/merchant/markets"), merchant), toJson(noHours)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[?(@.field == 'openingHours')].code").value("Size"))
        .andExpect(jsonPath("$.errors[?(@.field == 'name')].code").value("Size"));

    Map<String, Object> badHours = marketProfile("Testmarkt");
    badHours.put("openingHours", List.of(Map.of("dayOfWeek", 1, "opensAt", "18:00", "closesAt", "08:00")));
    mvc.perform(json(auth(post("/api/v1/merchant/markets"), merchant), toJson(badHours)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("AfterOpensAt"));

    Map<String, Object> deliveryWithoutRadius = marketProfile("Testmarkt");
    Map<String, Object> fulfilment = new HashMap<>();
    fulfilment.put("delivery", true);
    fulfilment.put("pickup", false);
    fulfilment.put("minimumOrderValue", Map.of("amountCents", 0, "currency", "EUR"));
    deliveryWithoutRadius.put("fulfilment", fulfilment);
    mvc.perform(json(auth(post("/api/v1/merchant/markets"), merchant), toJson(deliveryWithoutRadius)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("fulfilment.deliveryRadiusKm"));
  }

  @Test
  void listingsFromTheSharedCatalogAndPrivateProductsSearchableInEveryLanguage() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Katalog Testmarkt");

    // Shared catalog search (any language) knows whether the merchant's only market lists a product.
    MvcResult catalog =
        mvc.perform(auth(get("/api/v1/merchant/catalog").param("q", "تفاح").param("size", "50"), merchant))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(strings(catalog, "$.items[*].name")).contains("Äpfel Elstar");
    assertThat(booleans(catalog, "$.items[*].alreadyListed")).containsOnly(false);

    UUID apples = addListing(merchant, market, "Äpfel Elstar", 279);
    MvcResult again = mvc.perform(auth(get("/api/v1/merchant/catalog").param("q", "apple"), merchant)).andReturn();
    assertThat(booleans(again, "$.items[?(@.name == 'Äpfel Elstar')].alreadyListed")).containsExactly(true);
    UUID catalogApples =
        UUID.fromString(strings(again, "$.items[?(@.name == 'Äpfel Elstar')].id").getFirst());
    mvc.perform(
            json(
                auth(post("/api/v1/merchant/markets/" + market + "/listings"), merchant),
                toJson(Map.of("catalogProductId", catalogApples, "price", Map.of("amountCents", 100, "currency", "EUR")))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("LISTING_EXISTS"));
    mvc.perform(
            json(
                auth(post("/api/v1/merchant/markets/" + market + "/listings"), merchant),
                toJson(Map.of("price", Map.of("amountCents", 100, "currency", "EUR")))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("ExactlyOne"));

    // A private product with German and Arabic names plus an English keyword.
    UUID category = jdbc.sql("SELECT id FROM category WHERE slug = 'deli'").query(UUID.class).single();
    Map<String, Object> newProduct = new HashMap<>();
    newProduct.put("name", Map.of("de", "Omas Quittengelee", "ar", "هلام السفرجل من الجدة"));
    newProduct.put("tagline", Map.of("de", "nach Familienrezept"));
    newProduct.put("keywords", List.of("quince jelly", "ayva"));
    newProduct.put("unitSize", "250 g");
    newProduct.put("netQuantity", 0.25);
    newProduct.put("netUnit", "kg");
    newProduct.put("categoryId", category);
    newProduct.put("cuisineCodes", List.of("TURKISH"));
    newProduct.put("allergens", List.of());
    MvcResult privateListing =
        mvc.perform(
                json(
                    auth(post("/api/v1/merchant/markets/" + market + "/listings"), merchant),
                    toJson(Map.of("newProduct", newProduct, "price", Map.of("amountCents", 450, "currency", "EUR"), "maxOrderQuantity", 6))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.privateProduct").value(true))
            .andExpect(jsonPath("$.isNew").value(true))
            .andExpect(jsonPath("$.maxOrderQuantity").value(6))
            .andExpect(jsonPath("$.comparisonPrice.price.amountCents").value(1800))
            .andReturn();
    String jelly = read(privateListing, "$.id");

    for (String query : List.of("سفرجل", "السفرجل", "quince", "QUITTEN", "ayva")) {
      MvcResult found = mvc.perform(get("/api/v1/markets/" + market + "/products").param("q", query)).andReturn();
      assertThat(strings(found, "$.items[*].id")).as(query).containsExactly(jelly);
    }
    // English is missing → fallback to the market's primary locale (de).
    mvc.perform(get("/api/v1/products/" + jelly).header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
        .andExpect(jsonPath("$.name").value("Omas Quittengelee"))
        .andExpect(jsonPath("$.tagline").value("nach Familienrezept"));
    mvc.perform(get("/api/v1/products/" + jelly).header(HttpHeaders.ACCEPT_LANGUAGE, "ar"))
        .andExpect(jsonPath("$.name").value("هلام السفرجل من الجدة"))
        .andExpect(jsonPath("$.cuisines[0].name").value("تركي"));
    // Private products do not appear in the shared catalog.
    MvcResult shared = mvc.perform(auth(get("/api/v1/merchant/catalog").param("q", "quitten"), merchant)).andReturn();
    assertThat((Integer) read(shared, "$.totalItems")).isZero();

    // Primary locale name is required.
    newProduct.put("name", Map.of("en", "Jam"));
    mvc.perform(
            json(
                auth(post("/api/v1/merchant/markets/" + market + "/listings"), merchant),
                toJson(Map.of("newProduct", newProduct, "price", Map.of("amountCents", 450, "currency", "EUR")))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("PrimaryLocaleRequired"));

    // Update price and availability; the listing view includes unavailable listings.
    mvc.perform(
            json(
                auth(put("/api/v1/merchant/markets/" + market + "/listings/" + apples), merchant),
                "{\"price\":{\"amountCents\":259,\"currency\":\"EUR\"},\"available\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.price.amountCents").value(259))
        .andExpect(jsonPath("$.available").value(false))
        .andExpect(jsonPath("$.privateProduct").value(false));
    MvcResult listings = mvc.perform(auth(get("/api/v1/merchant/markets/" + market + "/listings"), merchant)).andReturn();
    assertThat((Integer) read(listings, "$.totalItems")).isEqualTo(2);
    // Price history: the original and the new price.
    assertThat(jdbc.sql("SELECT count(*) FROM market_product_price WHERE market_product_id = :id").param("id", apples).query(Integer.class).single())
        .isEqualTo(2);

    mvc.perform(auth(delete("/api/v1/merchant/markets/" + market + "/listings/" + apples), merchant)).andExpect(status().isNoContent());
    mvc.perform(auth(delete("/api/v1/merchant/markets/" + market + "/listings/" + apples), merchant)).andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/products/" + apples)).andExpect(status().isNotFound());
    // Deleting frees the catalog product for a new listing.
    addListing(merchant, market, "Äpfel Elstar", 289);
  }

  @Test
  void promotionsAreCreatedPricedAndEnded() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Angebots Testmarkt");
    UUID ayran = addListing(merchant, market, "Ayran", 150);
    UUID tea = addListing(merchant, market, "Schwarzer Tee Rize", 800);

    OffsetDateTime from = OffsetDateTime.ofInstant(clock.instant().minus(Duration.ofHours(1)), ZoneOffset.UTC);
    Map<String, Object> percent = new HashMap<>();
    percent.put("title", Map.of("de", "Teewoche", "ar", "أسبوع الشاي"));
    percent.put("type", "PERCENT_OFF");
    percent.put("percentOff", 25);
    percent.put("validFrom", from.toString());
    percent.put("validUntil", from.plusDays(30).toString());
    percent.put("productIds", List.of(tea));
    MvcResult created =
        mvc.perform(json(auth(post("/api/v1/merchant/markets/" + market + "/promotions"), merchant), toJson(percent)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("Teewoche"))
            .andExpect(jsonPath("$.percentOff").value(25))
            .andExpect(jsonPath("$.productIds[0]").value(tea.toString()))
            .andReturn();
    String promotionId = read(created, "$.id");

    Map<String, Object> multi = new HashMap<>(percent);
    multi.put("title", Map.of("de", "3 für 3"));
    multi.put("type", "MULTI_BUY");
    multi.remove("percentOff");
    multi.put("bundleQuantity", 3);
    multi.put("bundlePrice", Map.of("amountCents", 300, "currency", "EUR"));
    multi.put("productIds", List.of(ayran));
    mvc.perform(json(auth(post("/api/v1/merchant/markets/" + market + "/promotions"), merchant), toJson(multi)))
        .andExpect(status().isCreated());

    mvc.perform(get("/api/v1/products/" + tea).header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
        .andExpect(jsonPath("$.promotionalPrice.amountCents").value(600))
        .andExpect(jsonPath("$.promotion.label").value("25% off"))
        .andExpect(jsonPath("$.promotion.type").value("PERCENT_OFF"));
    mvc.perform(get("/api/v1/products/" + ayran))
        .andExpect(jsonPath("$.promotion.label").value("3 für 3,00 €"))
        .andExpect(jsonPath("$.promotionalPrice").isEmpty());
    assertThat(objects(mvc.perform(get("/api/v1/markets/" + market + "/promotions")).andReturn(), "$")).hasSize(2);

    // Products of other markets and inconsistent rules are rejected.
    Map<String, Object> foreign = new HashMap<>(percent);
    foreign.put("productIds", List.of(listingId(ANATOLIA, "Salatgurke")));
    mvc.perform(json(auth(post("/api/v1/merchant/markets/" + market + "/promotions"), merchant), toJson(foreign)))
        .andExpect(status().isNotFound());
    Map<String, Object> missingPercent = new HashMap<>(percent);
    missingPercent.remove("percentOff");
    mvc.perform(json(auth(post("/api/v1/merchant/markets/" + market + "/promotions"), merchant), toJson(missingPercent)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("percentOff"));

    mvc.perform(auth(delete("/api/v1/merchant/markets/" + market + "/promotions/" + promotionId), merchant)).andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/products/" + tea)).andExpect(jsonPath("$.promotion").isEmpty());
    MvcResult all = mvc.perform(auth(get("/api/v1/merchant/markets/" + market + "/promotions"), merchant)).andReturn();
    assertThat(objects(all, "$")).hasSize(2);
    assertThat(objects(mvc.perform(get("/api/v1/markets/" + market + "/promotions")).andReturn(), "$")).hasSize(1);
  }

  @Test
  void merchantsOnlySeeTheirOwnMarketsAndCustomersAreForbidden() throws Exception {
    String owner = registerMerchant();
    String other = registerMerchant();
    String customer = registerUser();
    UUID market = createMarket(owner, "Eigener Testmarkt");
    UUID listing = addListing(owner, market, "Ayran", 150);

    mvc.perform(auth(get("/api/v1/merchant/markets/" + market + "/listings"), other)).andExpect(status().isNotFound());
    mvc.perform(json(auth(put("/api/v1/merchant/markets/" + market), other), toJson(marketProfile("Übernahme"))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    mvc.perform(json(auth(put("/api/v1/merchant/markets/" + market + "/listings/" + listing), other), "{\"available\":false}"))
        .andExpect(status().isNotFound());
    mvc.perform(auth(delete("/api/v1/merchant/markets/" + market + "/listings/" + listing), other)).andExpect(status().isNotFound());
    mvc.perform(auth(get("/api/v1/merchant/markets/" + market + "/promotions"), other)).andExpect(status().isNotFound());
    mvc.perform(auth(get("/api/v1/merchant/markets/" + market + "/orders"), other)).andExpect(status().isNotFound());
    assertThat(strings(mvc.perform(auth(get("/api/v1/merchant/markets"), other)).andReturn(), "$[*].id"))
        .doesNotContain(market.toString());

    mvc.perform(auth(get("/api/v1/merchant/markets"), customer))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    mvc.perform(json(auth(post("/api/v1/merchant/markets"), customer), toJson(marketProfile("Kunde"))))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/merchant/markets")).andExpect(status().isUnauthorized());
  }
}
