package de.dukkan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.support.IntegrationTest;
import de.dukkan.support.TestImages;
import java.awt.Color;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/** F1 without a language model: dictated or typed lists are read by rules, photos are declined. */
class ShoppingListIntegrationTest extends IntegrationTest {

  @BeforeEach
  void switchOn() {
    setFeature("SHOPPING_LIST", true);
    setFeature("SHOPPING_LIST_MEDIA", true);
  }

  @Test
  void dictatedListIsMatchedToTheMarketsProductsWithPackCounts() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Einkaufslisten-Markt");
    UUID cucumber = addListing(merchant, market, "Salatgurke", 69);
    UUID tomatoes = addListing(merchant, market, "Strauchtomaten", 249);
    UUID eggs = addListing(merchant, market, "Eier aus Freilandhaltung", 399);
    // Starts with "Tomaten" (first for search-as-you-type), but is not tomatoes.
    UUID paste = addListing(merchant, market, "Tomatenmark", 129);
    String customer = registerUser();

    mvc.perform(get("/api/v1/shopping-list"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ai").value(false))
        .andExpect(jsonPath("$.maxImages").value(4));

    MvcResult result =
        mvc.perform(
                list(market, customer)
                    .param("text", "Ich brauche 2 Gurken, 1 kg Tomaten und 8 Eier, Einhorn-Schokolade")
                    .header(HttpHeaders.ACCEPT_LANGUAGE, "de"))
            .andExpect(status().isOk())
            .andReturn();
    assertThat((String) read(result, "$.engine")).isEqualTo("RULES");
    assertThat(strings(result, "$.lines[*].text")).containsExactly("2 Gurken", "1 kg Tomaten", "8 Eier", "Einhorn-Schokolade");
    assertThat((String) read(result, "$.lines[0].match.product.id")).isEqualTo(cucumber.toString());
    assertThat((Integer) read(result, "$.lines[0].match.quantity")).isEqualTo(2);
    // 1 kg with a 500 g pack: two packs.
    assertThat((String) read(result, "$.lines[1].match.product.id")).isEqualTo(tomatoes.toString());
    assertThat((String) read(result, "$.lines[1].amount")).isEqualTo("1 kg");
    assertThat((Integer) read(result, "$.lines[1].match.quantity")).isEqualTo(2);
    assertThat(strings(result, "$.lines[1].alternatives[*].product.id")).containsExactly(paste.toString());
    // 8 eggs, sold by 10.
    assertThat((String) read(result, "$.lines[2].match.product.id")).isEqualTo(eggs.toString());
    assertThat((Integer) read(result, "$.lines[2].match.quantity")).isEqualTo(1);
    assertThat(object(result, "$.lines[3].match")).isNull();
    assertThat(objects(result, "$.lines[3].alternatives")).isEmpty();
    ProductDraftIntegrationTest.conforms("ShoppingListResult", object(result, "$"));
  }

  @Test
  void photosNeedTheLanguageModelAndCustomersMustSignIn() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Foto-Markt");
    String customer = registerUser();
    MockMultipartFile photo = new MockMultipartFile("images", "liste.png", "image/png", TestImages.png(40, 40, Color.WHITE));

    mvc.perform(list(market, customer).file(photo))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("SHOPPING_LIST_AI_UNAVAILABLE"));
    mvc.perform(list(market, customer)).andExpect(status().isBadRequest());
    mvc.perform(multipart("/api/v1/markets/" + market + "/shopping-list").param("text", "Brot")).andExpect(status().isUnauthorized());
    mvc.perform(list(UUID.randomUUID(), customer).param("text", "Brot"))
        .andExpect(status().isNotFound());
    MockMultipartFile gif = new MockMultipartFile("images", "a.gif", "image/gif", new byte[] {1, 2, 3});
    mvc.perform(list(market, customer).file(gif).param("text", "Brot"))
        .andExpect(status().isBadRequest());
  }

  private static MockMultipartHttpServletRequestBuilder list(UUID market, String token) {
    return multipart("/api/v1/markets/" + market + "/shopping-list").header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
  }
}
