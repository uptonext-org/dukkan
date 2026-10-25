package de.dukkan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.support.IntegrationTest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

/** A merchant adjusts a shared catalog product for their market only (merchant point 2). */
class ListingOverridesIntegrationTest extends IntegrationTest {

  private UUID catalogAyran() {
    return jdbc.sql(
            "SELECT catalog_product_id FROM catalog_product_translation t JOIN catalog_product cp ON cp.id = t.catalog_product_id"
                + " WHERE t.locale = 'de' AND t.name = 'Ayran' AND cp.owner_market_id IS NULL")
        .query(UUID.class)
        .single();
  }

  private static Map<String, Object> ownAyran() {
    Map<String, Object> overrides = new HashMap<>();
    overrides.put("name", "  Hausgemachter Ayran ");
    overrides.put("description", "Jeden Morgen frisch gerührt.");
    overrides.put("unitSize", "500 ml");
    overrides.put("brand", "");
    return overrides;
  }

  @Test
  void ownDetailsShowEverywhereInTheMarketButNeverChangeTheCatalog() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Eigener Ayran Markt");
    UUID ayran = catalogAyran();
    String catalogUnit = jdbc.sql("SELECT unit_size FROM catalog_product WHERE id = :id").param("id", ayran).query(String.class).single();

    MvcResult created =
        mvc.perform(
                json(
                    auth(post("/api/v1/merchant/markets/" + market + "/listings"), merchant),
                    toJson(Map.of("catalogProductId", ayran, "price", Map.of("amountCents", 149, "currency", "EUR"), "overrides", ownAyran()))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Hausgemachter Ayran"))
            .andExpect(jsonPath("$.unitSize").value("500 ml"))
            .andExpect(jsonPath("$.overrides.name").value("Hausgemachter Ayran"))
            .andExpect(jsonPath("$.overrides.brand").doesNotExist())
            .andReturn();
    UUID listing = UUID.fromString(read(created, "$.id"));

    // Customers see the own details in every language, and find the listing by its own name.
    mvc.perform(get("/api/v1/products/" + listing).header(HttpHeaders.ACCEPT_LANGUAGE, "ar"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Hausgemachter Ayran"))
        .andExpect(jsonPath("$.unitSize").value("500 ml"))
        .andExpect(jsonPath("$.description").value("Jeden Morgen frisch gerührt."));
    assertThat(strings(mvc.perform(get("/api/v1/markets/" + market + "/products").param("q", "hausgemacht")).andReturn(), "$.items[*].id"))
        .containsExactly(listing.toString());
    // The catalog name still finds it too.
    assertThat(strings(mvc.perform(get("/api/v1/markets/" + market + "/products").param("q", "ayran")).andReturn(), "$.items[*].id"))
        .contains(listing.toString());

    // Orders keep the name the customer saw.
    String customer = registerUser();
    confirmEmail(customer);
    String order = orderPickup(customer, market, listing, 4);
    mvc.perform(auth(get("/api/v1/orders/" + order), customer)).andExpect(jsonPath("$.items[0].name").value("Hausgemachter Ayran"));

    // The shared catalog and other markets are untouched.
    assertThat(jdbc.sql("SELECT name FROM catalog_product_translation WHERE catalog_product_id = :id AND locale = 'de'").param("id", ayran).query(String.class).single())
        .isEqualTo("Ayran");
    String other = registerMerchant();
    UUID otherMarket = createMarket(other, "Anderer Markt");
    UUID otherListing = addListing(other, otherMarket, "Ayran", 99);
    mvc.perform(get("/api/v1/products/" + otherListing))
        .andExpect(jsonPath("$.name").value("Ayran"))
        .andExpect(jsonPath("$.unitSize").value(catalogUnit));

    // Back to the catalog values: all fields empty.
    mvc.perform(
            json(
                auth(put("/api/v1/merchant/markets/" + market + "/listings/" + listing), merchant),
                "{\"overrides\":{\"name\":null,\"description\":\" \",\"unitSize\":null,\"brand\":null}}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Ayran"))
        .andExpect(jsonPath("$.unitSize").value(catalogUnit))
        .andExpect(jsonPath("$.overrides").doesNotExist());
    assertThat(strings(mvc.perform(get("/api/v1/markets/" + market + "/products").param("q", "hausgemacht")).andReturn(), "$.items[*].id")).isEmpty();

    // A price change leaves the own details alone.
    mvc.perform(
            json(auth(put("/api/v1/merchant/markets/" + market + "/listings/" + listing), merchant), toJson(Map.of("overrides", Map.of("name", "Ayran vom Hof")))))
        .andExpect(jsonPath("$.name").value("Ayran vom Hof"));
    mvc.perform(json(auth(put("/api/v1/merchant/markets/" + market + "/listings/" + listing), merchant), toJson(Map.of("price", Map.of("amountCents", 159, "currency", "EUR")))))
        .andExpect(jsonPath("$.name").value("Ayran vom Hof"))
        .andExpect(jsonPath("$.overrides.name").value("Ayran vom Hof"));
  }

  @Test
  void ownDetailsAreForCatalogProductsAndLimited() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Grenzen Markt");
    UUID category = jdbc.sql("SELECT id FROM category WHERE slug = 'deli'").query(UUID.class).single();
    Map<String, Object> newProduct = new HashMap<>();
    newProduct.put("name", Map.of("de", "Omas Quittengelee"));
    newProduct.put("unitSize", "250 g");
    newProduct.put("categoryId", category);
    mvc.perform(
            json(
                auth(post("/api/v1/merchant/markets/" + market + "/listings"), merchant),
                toJson(Map.of("newProduct", newProduct, "price", Map.of("amountCents", 450, "currency", "EUR"), "overrides", Map.of("name", "Anders")))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("CatalogProductOnly"));
    mvc.perform(
            json(
                auth(post("/api/v1/merchant/markets/" + market + "/listings"), merchant),
                toJson(Map.of("catalogProductId", catalogAyran(), "price", Map.of("amountCents", 149, "currency", "EUR"), "overrides", Map.of("unitSize", "x".repeat(41))))))
        .andExpect(status().isBadRequest());
    assertThat(objects(mvc.perform(auth(get("/api/v1/merchant/markets/" + market + "/listings"), merchant)).andReturn(), "$.items")).isEqualTo(List.of());
  }
}
