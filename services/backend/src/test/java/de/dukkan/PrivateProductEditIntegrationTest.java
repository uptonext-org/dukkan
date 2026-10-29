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

/** A merchant edits every detail of their own product after creating it, not only the price. */
class PrivateProductEditIntegrationTest extends IntegrationTest {

  private UUID category(String slug) {
    return jdbc.sql("SELECT id FROM category WHERE slug = :slug").param("slug", slug).query(UUID.class).single();
  }

  private Map<String, Object> product(String germanName, String unitSize, String categorySlug) {
    Map<String, Object> product = new HashMap<>();
    product.put("name", Map.of("de", germanName));
    product.put("unitSize", unitSize);
    product.put("categoryId", category(categorySlug));
    product.put("allergens", List.of());
    return product;
  }

  @Test
  void ownProductDetailsCanBeChangedAndReadBackForTheForm() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Mata Markt");
    MvcResult created =
        mvc.perform(
                json(
                    auth(post("/api/v1/merchant/markets/" + market + "/listings"), merchant),
                    toJson(Map.of("newProduct", product("mata", "500 g", "deli"), "price", Map.of("amountCents", 300, "currency", "EUR")))))
            .andExpect(status().isCreated())
            .andReturn();
    String listing = read(created, "$.id");

    Map<String, Object> changed = product("Matbucha", "400 g", "spices");
    changed.put("name", Map.of("de", "Matbucha", "ar", "مطبوخة"));
    changed.put("description", Map.of("de", "Gekochter Tomaten-Paprika-Salat."));
    changed.put("keywords", List.of("matbucha", "salat"));
    changed.put("brand", "Hausgemacht");
    changed.put("cuisineCodes", List.of("ARABIC"));
    changed.put("organic", true);
    changed.put("allergens", List.of("celery"));
    changed.put("barcode", "4006381333931");
    mvc.perform(
            json(
                auth(put("/api/v1/merchant/markets/" + market + "/listings/" + listing), merchant),
                toJson(Map.of("product", changed, "price", Map.of("amountCents", 349, "currency", "EUR"), "maxOrderQuantity", 5))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Matbucha"))
        .andExpect(jsonPath("$.unitSize").value("400 g"))
        .andExpect(jsonPath("$.brand").value("Hausgemacht"))
        .andExpect(jsonPath("$.organic").value(true))
        .andExpect(jsonPath("$.price.amountCents").value(349))
        .andExpect(jsonPath("$.maxOrderQuantity").value(5));

    // The edit form gets every detail back, in every language.
    mvc.perform(auth(get("/api/v1/merchant/markets/" + market + "/listings/" + listing + "/details"), merchant))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.listing.id").value(listing))
        .andExpect(jsonPath("$.listing.privateProduct").value(true))
        .andExpect(jsonPath("$.product.name.de").value("Matbucha"))
        .andExpect(jsonPath("$.product.name.ar").value("مطبوخة"))
        .andExpect(jsonPath("$.product.description.de").value("Gekochter Tomaten-Paprika-Salat."))
        .andExpect(jsonPath("$.product.keywords[0]").value("matbucha"))
        .andExpect(jsonPath("$.product.categoryId").value(category("spices").toString()))
        .andExpect(jsonPath("$.product.cuisineCodes[0]").value("ARABIC"))
        .andExpect(jsonPath("$.product.allergens[0]").value("celery"))
        .andExpect(jsonPath("$.product.barcode").value("4006381333931"));

    // Customers see the new name in Arabic and find the product by the new name and keyword only.
    mvc.perform(get("/api/v1/products/" + listing).header(HttpHeaders.ACCEPT_LANGUAGE, "ar")).andExpect(jsonPath("$.name").value("مطبوخة"));
    for (String query : List.of("matbucha", "مطبوخة", "salat")) {
      assertThat(strings(mvc.perform(get("/api/v1/markets/" + market + "/products").param("q", query)).andReturn(), "$.items[*].id"))
          .as(query)
          .containsExactly(listing);
    }
    assertThat(strings(mvc.perform(get("/api/v1/markets/" + market + "/products").param("q", "mata")).andReturn(), "$.items[*].id")).isEmpty();
  }

  @Test
  void sharedCatalogProductsAreAdjustedWithOverridesNotChanged() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Katalog Markt");
    UUID listing = addListing(merchant, market, "Ayran", 149);

    mvc.perform(
            json(
                auth(put("/api/v1/merchant/markets/" + market + "/listings/" + listing), merchant),
                toJson(Map.of("product", product("Mein Ayran", "1 l", "drinks")))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("PrivateProductOnly"));
    mvc.perform(auth(get("/api/v1/merchant/markets/" + market + "/listings/" + listing + "/details"), merchant))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.listing.privateProduct").value(false))
        .andExpect(jsonPath("$.product.name.de").value("Ayran"));

    // Only the owner sees the details.
    String other = registerMerchant();
    mvc.perform(auth(get("/api/v1/merchant/markets/" + market + "/listings/" + listing + "/details"), other)).andExpect(status().isNotFound());
  }
}
