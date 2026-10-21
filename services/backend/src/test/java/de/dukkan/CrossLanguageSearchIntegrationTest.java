package de.dukkan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.support.IntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

/** A product is found in every language: names of all translations, brand and keywords are searched. */
class CrossLanguageSearchIntegrationTest extends IntegrationTest {

  @ParameterizedTest
  @ValueSource(strings = {"تفاح", "apple", "apfel", "Äpfel", "APFEL", "elma", "تُفّاح"})
  void searchInAnyLanguageFindsTheSameApples(String query) throws Exception {
    UUID anatolia = marketId(ANATOLIA);
    UUID apples = listingId(ANATOLIA, "Äpfel Elstar");

    MvcResult result =
        mvc.perform(get("/api/v1/markets/" + anatolia + "/products").param("q", query)).andExpect(status().isOk()).andReturn();

    assertThat(strings(result, "$.items[*].id")).contains(apples.toString());
  }

  @Test
  void resultsAreShownInTheRequestedLanguage() throws Exception {
    UUID anatolia = marketId(ANATOLIA);
    UUID apples = listingId(ANATOLIA, "Äpfel Elstar");

    MvcResult arabic =
        mvc.perform(get("/api/v1/markets/" + anatolia + "/products").param("q", "apfel").header(HttpHeaders.ACCEPT_LANGUAGE, "ar"))
            .andExpect(header().string(HttpHeaders.CONTENT_LANGUAGE, "ar"))
            .andReturn();
    assertThat(strings(arabic, "$.items[?(@.id == '" + apples + "')].name")).containsExactly("تفاح إلستار");
    assertThat(strings(arabic, "$.items[?(@.id == '" + apples + "')].tagline")).containsExactly("حلو وحامض");

    MvcResult english =
        mvc.perform(get("/api/v1/products/" + apples).header(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9"))
            .andExpect(header().string(HttpHeaders.CONTENT_LANGUAGE, "en"))
            .andReturn();
    assertThat((String) read(english, "$.name")).isEqualTo("Elstar Apples");
    assertThat((String) read(english, "$.description")).startsWith("Crisp dessert apples");
    assertThat((String) read(english, "$.origin")).isEqualTo("DE");
    assertThat((String) read(english, "$.comparisonPrice.unit")).isEqualTo("kg");
  }

  @Test
  void localisedNamePrefixMatchesRankFirst() throws Exception {
    UUID elbe = marketId("elbe-frische-supermarkt");
    MvcResult german = mvc.perform(get("/api/v1/markets/" + elbe + "/products").param("q", "apf")).andReturn();
    List<String> names = read(german, "$.items[*].name");
    // Localised (German) names starting with "apf" ("Äpfel Elstar", "Apfelschorle") rank before
    // substring matches such as "Bio-Äpfel Braeburn".
    assertThat(names).contains("Äpfel Elstar", "Apfelschorle", "Bio-Äpfel Braeburn");
    assertThat(names.indexOf("Bio-Äpfel Braeburn"))
        .isGreaterThan(names.indexOf("Äpfel Elstar"))
        .isGreaterThan(names.indexOf("Apfelschorle"));
    assertThat(names.subList(0, 2)).containsExactlyInAnyOrder("Äpfel Elstar", "Apfelschorle");
  }

  @Test
  void suggestionsAreLocalisedNames() throws Exception {
    UUID anatolia = marketId(ANATOLIA);
    MvcResult arabic =
        mvc.perform(get("/api/v1/markets/" + anatolia + "/search/suggestions").param("q", "tomate").header(HttpHeaders.ACCEPT_LANGUAGE, "ar"))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(strings(arabic, "$")).contains("طماطم عنقودية");
    MvcResult german = mvc.perform(get("/api/v1/markets/" + anatolia + "/search/suggestions").param("q", "طماطم")).andReturn();
    assertThat(strings(german, "$")).contains("Strauchtomaten");
  }

  @Test
  void nearbySearchComparesOffersAcrossMarkets() throws Exception {
    MvcResult result =
        mvc.perform(
                get("/api/v1/search/products")
                    .param("q", "تفاح")
                    .param("lat", String.valueOf(ST_GEORG_LAT))
                    .param("lng", String.valueOf(ST_GEORG_LNG))
                    .param("radiusKm", "15")
                    .param("sort", "price_asc")
                    .header(HttpHeaders.ACCEPT_LANGUAGE, "de"))
            .andExpect(status().isOk())
            .andReturn();
    List<String> markets = read(result, "$.items[*].market.slug");
    assertThat(markets).contains(ANATOLIA, "elbe-frische-supermarkt", "gruenkorb-bioladen");
    List<Integer> prices = read(result, "$.items[*].product.price.amountCents");
    assertThat(prices).isSorted();
    // Every hit has "تفاح" in its Arabic name; German names: Äpfel Elstar, Bio-Äpfel Braeburn, Apfelschorle.
    assertThat(strings(result, "$.items[*].product.name"))
        .allSatisfy(n -> assertThat(de.dukkan.i18n.application.TextNormalizer.normalize(n)).contains("apfel"));
    assertThat(ints(result, "$.items[*].market.distanceMeters")).doesNotContainNull();

    MvcResult byDistance =
        mvc.perform(
                get("/api/v1/search/products")
                    .param("q", "apple")
                    .param("lat", String.valueOf(ST_GEORG_LAT))
                    .param("lng", String.valueOf(ST_GEORG_LNG))
                    .param("radiusKm", "15")
                    .param("sort", "distance"))
            .andReturn();
    assertThat(ints(byDistance, "$.items[*].market.distanceMeters")).isSorted();
    // Relevance: a whole first word ("apfel" = "Äpfel Elstar") beats a longer prefix ("Apfelschorle"),
    // in German and Arabic (in English "apple" is the first word of "Apple spritzer", so that one wins).
    for (String q : new String[] {"apfel", "Äpfel", "تفاح"}) {
      MvcResult relevant =
          mvc.perform(
                  get("/api/v1/search/products")
                      .param("q", q)
                      .param("lat", String.valueOf(ST_GEORG_LAT))
                      .param("lng", String.valueOf(ST_GEORG_LNG))
                      .param("radiusKm", "15")
                      .header(HttpHeaders.ACCEPT_LANGUAGE, "de"))
              .andReturn();
      assertThat(strings(relevant, "$.items[*].product.name")).as(q).first().isEqualTo("Äpfel Elstar");
    }
    mvc.perform(get("/api/v1/search/products").param("q", "a").param("lat", "53.5").param("lng", "10"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void filtersNewOrganicAndPromoted() throws Exception {
    UUID gruenkorb = marketId("gruenkorb-bioladen");
    MvcResult organic = mvc.perform(get("/api/v1/markets/" + gruenkorb + "/products").param("organicOnly", "true").param("size", "100")).andReturn();
    assertThat(booleans(organic, "$.items[*].organic")).isNotEmpty().containsOnly(true);

    UUID anatolia = marketId(ANATOLIA);
    MvcResult fresh = mvc.perform(get("/api/v1/markets/" + anatolia + "/products").param("newOnly", "true")).andReturn();
    assertThat((Integer) read(fresh, "$.totalItems")).isEqualTo(3);
    MvcResult promoted = mvc.perform(get("/api/v1/markets/" + anatolia + "/products").param("promotedOnly", "true").param("sort", "price_desc")).andReturn();
    assertThat(objects(promoted, "$.items[*].promotion")).isNotEmpty().doesNotContainNull();
    assertThat(ints(promoted, "$.items[*].price.amountCents")).isSortedAccordingTo(java.util.Comparator.reverseOrder());
  }
}
