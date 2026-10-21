package de.dukkan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.support.IntegrationTest;
import de.dukkan.support.OpenApiContract;
import de.dukkan.support.TestImages;
import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** B-33 without a language model: the rule-based parser, validation, ownership and the contract shape. */
class ProductDraftIntegrationTest extends IntegrationTest {

  static final String MEAT = "e717949d-a6bf-5996-b710-2be121395249";
  static final String GRAINS = "08a3b0c1-3cbd-5757-a4d6-db9a4a20c426";
  private static final OpenApiContract CONTRACT = OpenApiContract.load();

  ResultActions draft(String token, UUID marketId, String body, String language) throws Exception {
    return mvc.perform(
        json(auth(post("/api/v1/merchant/markets/" + marketId + "/product-drafts"), token), body)
            .header(HttpHeaders.ACCEPT_LANGUAGE, language));
  }

  String uploadPhoto(String token, UUID marketId, byte[] png) throws Exception {
    MvcResult result =
        mvc.perform(
                multipart("/api/v1/merchant/markets/" + marketId + "/media")
                    .file(new MockMultipartFile("file", "pack.png", "image/png", png))
                    .param("purpose", "PRODUCT")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isCreated())
            .andReturn();
    return read(result, "$.key");
  }

  /** Required properties present, no undocumented ones (the same check as ResponseSchemaIntegrationTest). */
  static void conforms(String schema, Object node) {
    assertThat(node).as(schema).isInstanceOf(Map.class);
    List<String> keys = ((Map<?, ?>) node).keySet().stream().map(String::valueOf).toList();
    assertThat(keys).as(schema).containsAll(CONTRACT.requiredFields(schema)).isSubsetOf(CONTRACT.declaredFields(schema));
  }

  @Test
  void parsesAGermanNoteAndFindsTheSharedCatalogProduct() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Erfassungsmarkt");

    MvcResult result =
        draft(merchant, market, "{\"text\":\"Sucuk Gazi 400 g 4,49 €\"}", "de")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.engine").value("RULES"))
            .andExpect(jsonPath("$.product.name.de").value("Sucuk Gazi"))
            .andExpect(jsonPath("$.product.unitSize").value("400 g"))
            .andExpect(jsonPath("$.product.netQuantity").value(0.4))
            .andExpect(jsonPath("$.product.netUnit").value("kg"))
            .andExpect(jsonPath("$.product.categoryId").value(MEAT))
            .andExpect(jsonPath("$.product.imageUrl").doesNotExist())
            .andExpect(jsonPath("$.price.amountCents").value(449))
            .andExpect(jsonPath("$.price.currency").value("EUR"))
            .andExpect(jsonPath("$.catalogMatches[0].name").value("Sucuk"))
            .andExpect(jsonPath("$.catalogMatches[0].alreadyListed").value(false))
            .andReturn();
    assertThat(strings(result, "$.warnings[*]")).containsExactly("AI_NOT_CONFIGURED");
    conforms("ProductDraft", object(result, "$"));
    conforms("ProductDraftProduct", object(result, "$.product"));
    conforms("CatalogProduct", objects(result, "$.catalogMatches[*]").getFirst());
  }

  @Test
  void arabicTextYieldsAnArabicNameAndArabicMatches() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "سوق الشام");

    MvcResult result =
        draft(merchant, market, "{\"text\":\"عدس أحمر ١ كيلو ٢٫٩٩\"}", "ar")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.product.name.ar").value("عدس أحمر"))
            .andExpect(jsonPath("$.product.unitSize").value("1 kg"))
            .andExpect(jsonPath("$.product.categoryId").value(GRAINS))
            .andExpect(jsonPath("$.price.amountCents").value(299))
            .andReturn();
    assertThat(strings(result, "$.catalogMatches[*].name")).first().isEqualTo("عدس أحمر");
  }

  @Test
  void aPhotoWithoutLanguageModelIsKeptButNotAnalysed() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Fotomarkt Erfassung");
    String key = uploadPhoto(merchant, market, TestImages.png(32, 32, new Color(0x2a5d3b)));

    MvcResult result =
        draft(merchant, market, "{\"imageKey\":\"" + key + "\"}", "de")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.engine").value("RULES"))
            .andExpect(jsonPath("$.product.imageUrl").value("http://localhost/api/v1/media/" + key))
            .andExpect(jsonPath("$.price").doesNotExist())
            .andExpect(jsonPath("$.catalogMatches").isEmpty())
            .andReturn();
    assertThat(strings(result, "$.warnings[*]"))
        .containsExactly(
            "AI_NOT_CONFIGURED", "IMAGE_NOT_ANALYSED", "NAME_MISSING", "CATEGORY_UNCERTAIN", "UNIT_SIZE_MISSING", "PRICE_MISSING");
  }

  @Test
  void validatesTheRequest() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Prüfmarkt Erfassung");

    draft(merchant, market, "{}", "de").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    draft(merchant, market, "{\"text\":\"   \"}", "de").andExpect(status().isBadRequest());
    draft(merchant, market, "{\"text\":\"" + "a".repeat(1001) + "\"}", "de")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("text"));
    draft(merchant, market, "{\"imageKey\":\"../../etc/passwd\"}", "de")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("imageKey"));
    draft(merchant, market, "{\"imageKey\":\"" + "0".repeat(64) + ".png\"}", "de")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("imageKey"))
        .andExpect(jsonPath("$.errors[0].code").value("NotFound"));
  }

  @Test
  void onlyTheOwnerMayDraftForAMarket() throws Exception {
    String owner = registerMerchant();
    UUID market = createMarket(owner, "Eigener Markt Erfassung");
    String body = "{\"text\":\"Ayran 250 ml\"}";

    draft(registerMerchant(), market, body, "de").andExpect(status().isNotFound());
    draft(registerUser(), market, body, "de").andExpect(status().isForbidden());
    mvc.perform(json(post("/api/v1/merchant/markets/" + market + "/product-drafts"), body)).andExpect(status().isUnauthorized());
    draft(owner, market, body, "de").andExpect(status().isOk());
  }
}
