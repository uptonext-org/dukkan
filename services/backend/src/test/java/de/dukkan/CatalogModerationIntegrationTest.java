package de.dukkan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.identity.application.IdentityService;
import de.dukkan.support.IntegrationTest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

/** B-36a: admins review products merchants create, correct texts and promote them to the shared catalog. */
class CatalogModerationIntegrationTest extends IntegrationTest {

  @Autowired private IdentityService identity;

  private String adminToken() throws Exception {
    String email = uniqueEmail();
    register(email, "CUSTOMER");
    identity.grantRole(identity.findByEmail(email).orElseThrow().id(), "ADMIN");
    return login(email);
  }

  /** Creates a private product with only a German name; returns its catalog product id. */
  private UUID privateProduct(String merchant, UUID market, String name, String unitSize, String barcode) throws Exception {
    UUID category = jdbc.sql("SELECT id FROM category WHERE slug = 'deli'").query(UUID.class).single();
    Map<String, Object> product = new HashMap<>();
    product.put("name", Map.of("de", name));
    product.put("unitSize", unitSize);
    product.put("categoryId", category);
    product.put("cuisineCodes", List.of());
    product.put("allergens", List.of());
    if (barcode != null) {
      product.put("barcode", barcode);
    }
    MvcResult created =
        mvc.perform(
                json(
                    auth(post("/api/v1/merchant/markets/" + market + "/listings"), merchant),
                    toJson(Map.of("newProduct", product, "price", Map.of("amountCents", 390, "currency", "EUR")))))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(read(created, "$.catalogProductId"));
  }

  @Test
  void adminsCorrectTextsAndPromoteProductsToTheSharedCatalog() throws Exception {
    String admin = adminToken();
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Moderationsmarkt");
    UUID medlar = privateProduct(merchant, market, "Mispelmus Moderation", "250 g", null);

    MvcResult queue = mvc.perform(auth(get("/api/v1/admin/catalog/products").param("size", "100"), admin)).andExpect(status().isOk()).andReturn();
    List<String> pending = strings(queue, "$.items[*].id");
    assertThat(pending).contains(medlar.toString());
    int index = pending.indexOf(medlar.toString());
    assertThat((String) read(queue, "$.items[" + index + "].market.name")).isEqualTo("Moderationsmarkt");
    assertThat((String) read(queue, "$.items[" + index + "].reviewStatus")).isEqualTo("PENDING");
    assertThat((String) read(queue, "$.items[" + index + "].texts.de.name")).isEqualTo("Mispelmus Moderation");
    ProductDraftIntegrationTest.conforms("CatalogReviewItem", object(queue, "$.items[" + index + "]"));
    ProductDraftIntegrationTest.conforms("CatalogReviewPage", object(queue, "$"));

    // A machine translation (simulated) is corrected; the flag goes away and search finds the new name.
    jdbc.sql("INSERT INTO catalog_product_translation (catalog_product_id, locale, name, search_name, machine_translated) VALUES (:id, 'en', 'Medlar jam', 'medlar jam', true)")
        .param("id", medlar)
        .update();
    mvc.perform(
            json(
                auth(put("/api/v1/admin/catalog/products/" + medlar + "/texts"), admin),
                toJson(Map.of("names", Map.of("en", "Medlar butter", "ar", "زبدة الزعرور"), "descriptions", Map.of("en", "Homemade")))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.texts.en.name").value("Medlar butter"))
        .andExpect(jsonPath("$.texts.en.description").value("Homemade"))
        .andExpect(jsonPath("$.texts.en.machineTranslated").value(false))
        .andExpect(jsonPath("$.texts.de.name").value("Mispelmus Moderation"));
    MvcResult found = mvc.perform(get("/api/v1/markets/" + market + "/products").param("q", "زعرور")).andReturn();
    assertThat((Integer) read(found, "$.totalItems")).isEqualTo(1);

    // Promoted: the product is shared, the market keeps its listing, other markets can list it.
    mvc.perform(json(auth(post("/api/v1/admin/catalog/products/" + medlar + "/review"), admin), "{\"decision\":\"PROMOTE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reviewStatus").value("PROMOTED"))
        .andExpect(jsonPath("$.market").doesNotExist());
    assertThat((Integer) read(mvc.perform(get("/api/v1/markets/" + market + "/products").param("q", "medlar")).andReturn(), "$.totalItems")).isEqualTo(1);
    String other = registerMerchant();
    UUID otherMarket = createMarket(other, "Zweiter Markt");
    mvc.perform(
            json(
                auth(post("/api/v1/merchant/markets/" + otherMarket + "/listings"), other),
                toJson(Map.of("catalogProductId", medlar, "price", Map.of("amountCents", 420, "currency", "EUR")))))
        .andExpect(status().isCreated());
    MvcResult shared = mvc.perform(auth(get("/api/v1/merchant/catalog").param("q", "Mispelmus Moderation"), other)).andReturn();
    assertThat(strings(shared, "$.items[*].id")).contains(medlar.toString());
    // Shared products are not reviewed again.
    mvc.perform(json(auth(post("/api/v1/admin/catalog/products/" + medlar + "/review"), admin), "{\"decision\":\"KEEP_PRIVATE\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
  }

  @Test
  void duplicatesOfSharedProductsAreShownAndABarcodeDuplicateCannotBePromoted() throws Exception {
    String admin = adminToken();
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Dublettenmarkt");
    // The seed's apples have barcode 2010000000000 and size 1 kg.
    UUID apples = privateProduct(merchant, market, "Äpfel Elstar", "1 kg", "2010000000000");

    MvcResult keptPrivate =
        mvc.perform(json(auth(post("/api/v1/admin/catalog/products/" + apples + "/review"), admin), "{\"decision\":\"KEEP_PRIVATE\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reviewStatus").value("KEPT_PRIVATE"))
            .andReturn();
    assertThat(strings(keptPrivate, "$.duplicates[*].barcode")).contains("2010000000000");
    mvc.perform(json(auth(post("/api/v1/admin/catalog/products/" + apples + "/review"), admin), "{\"decision\":\"PROMOTE\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PRODUCT_DUPLICATE"));
    assertThat(strings(mvc.perform(auth(get("/api/v1/admin/catalog/products").param("status", "KEPT_PRIVATE").param("size", "100"), admin)).andReturn(), "$.items[*].id"))
        .contains(apples.toString());

    // Only admins.
    mvc.perform(auth(get("/api/v1/admin/catalog/products"), merchant)).andExpect(status().isForbidden());
    mvc.perform(json(auth(put("/api/v1/admin/catalog/products/" + apples + "/texts"), admin), "{\"names\":{\"xx\":\"Test\"}}"))
        .andExpect(status().isBadRequest());
    mvc.perform(auth(get("/api/v1/admin/catalog/products").param("status", "DELETED"), admin)).andExpect(status().isBadRequest());
    mvc.perform(json(auth(post("/api/v1/admin/catalog/products/" + UUID.randomUUID() + "/review"), admin), "{\"decision\":\"PROMOTE\"}").header(HttpHeaders.ACCEPT_LANGUAGE, "de"))
        .andExpect(status().isNotFound());
  }

  @Test
  void productsThatLookLikeAlcoholOrTobaccoCarryAHintForMerchantAndAdmin() throws Exception {
    String admin = adminToken();
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Hinweismarkt");
    UUID raki = privateProduct(merchant, market, "Yeni Rakı", "70 cl", null);
    UUID ayran = privateProduct(merchant, market, "Ayran Hinweis", "250 ml", null);

    // The merchant sees the hint on the listing; nothing is blocked.
    mvc.perform(auth(get("/api/v1/merchant/markets/" + market + "/listings").param("size", "50"), merchant))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[?(@.catalogProductId == '" + raki + "')].restrictedGoodsHint").value(true))
        .andExpect(jsonPath("$.items[?(@.catalogProductId == '" + ayran + "')].restrictedGoodsHint").value(false));
    // The admin sees it in the catalog review.
    mvc.perform(auth(get("/api/v1/admin/catalog/products").param("size", "100"), admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[?(@.id == '" + raki + "')].restrictedGoodsHint").value(true))
        .andExpect(jsonPath("$.items[?(@.id == '" + ayran + "')].restrictedGoodsHint").value(false));
  }
}
