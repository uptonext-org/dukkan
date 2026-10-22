package de.dukkan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

class MarketHomeIntegrationTest extends IntegrationTest {

  @Test
  void homeComposesBannersPromotionsNewArrivalsAndTopCategories() throws Exception {
    UUID anatolia = marketId(ANATOLIA);
    MvcResult home =
        mvc.perform(get("/api/v1/markets/" + anatolia + "/home").header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_LANGUAGE, "en"))
            .andReturn();

    assertThat((String) read(home, "$.market.id")).isEqualTo(anatolia.toString());
    List<Map<String, Object>> banners = read(home, "$.banners");
    assertThat(banners).hasSizeBetween(2, 3);
    assertThat(banners).allSatisfy(b -> assertThat(b.get("tint")).isIn("sage", "sky", "blush", "sand", "rose"));
    assertThat(strings(home, "$.banners[*].target.type")).contains("PROMOTIONS", "NEW", "CATEGORY");
    assertThat((String) read(home, "$.banners[0].title")).isEqualTo("Our offers of the week");

    List<String> keys = read(home, "$.sections[*].key");
    // Best sellers depend on what other tests ordered here; the rest of the order is fixed.
    assertThat(keys.stream().filter(k -> !k.equals("BESTSELLERS")).toList())
        .startsWith("PROMOTIONS", "NEW")
        .contains("CATEGORY")
        .doesNotContain("FREQUENTLY_ORDERED");
    assertThat((String) read(home, "$.sections[0].title")).isEqualTo("Offers");
    List<Map<String, Object>> promoted = read(home, "$.sections[0].items");
    assertThat(promoted).isNotEmpty().allSatisfy(p -> assertThat(p.get("promotion")).isNotNull());
    List<Boolean> newFlags = read(home, "$.sections[1].items[*].isNew");
    assertThat(newFlags).isNotEmpty().containsOnly(true);
    List<String> categorySections = read(home, "$.sections[?(@.key == 'CATEGORY')].categoryId");
    assertThat(categorySections).hasSize(3).doesNotContainNull();
    // Anonymous callers get no favourite flags.
    assertThat(object(home, "$.sections[0].items[0].favorite")).isNull();
  }

  @Test
  void homeAndCategoriesAreLocalised() throws Exception {
    UUID anatolia = marketId(ANATOLIA);
    MvcResult arabic = mvc.perform(get("/api/v1/markets/" + anatolia + "/home").header(HttpHeaders.ACCEPT_LANGUAGE, "ar")).andReturn();
    assertThat((String) read(arabic, "$.sections[0].title")).isEqualTo("العروض");
    assertThat((String) read(arabic, "$.banners[0].overline")).isEqualTo("لفترة محدودة");

    MvcResult categories = mvc.perform(get("/api/v1/markets/" + anatolia + "/categories")).andExpect(status().isOk()).andReturn();
    List<Integer> sortOrders = read(categories, "$[*].sortOrder");
    assertThat(sortOrders).isSorted();
    assertThat(ints(categories, "$[*].productCount")).allSatisfy(c -> assertThat(c).isPositive());
    assertThat(strings(categories, "$[*].name")).contains("Obst & Gemüse");

    // The bakery only sells bakery products plus a few basics.
    MvcResult bakery = mvc.perform(get("/api/v1/markets/" + marketId(BAKERY) + "/categories")).andReturn();
    assertThat(strings(bakery, "$[*].slug")).contains("bakery").doesNotContain("meat", "frozen");
  }

  @Test
  void promotionsOfAMarketAreLocalisedAndPriced() throws Exception {
    UUID anatolia = marketId(ANATOLIA);
    MvcResult promotions =
        mvc.perform(get("/api/v1/markets/" + anatolia + "/promotions").header(HttpHeaders.ACCEPT_LANGUAGE, "en")).andReturn();
    assertThat(strings(promotions, "$[*].type")).contains("PERCENT_OFF", "MULTI_BUY");
    assertThat(strings(promotions, "$[*].title")).contains("Buy more, save more");

    String percentProduct = strings(promotions, "$[?(@.type == 'PERCENT_OFF')].productIds[0]").getFirst();
    MvcResult product = mvc.perform(get("/api/v1/products/" + percentProduct).header(HttpHeaders.ACCEPT_LANGUAGE, "de")).andReturn();
    int price = read(product, "$.price.amountCents");
    int promotional = read(product, "$.promotionalPrice.amountCents");
    assertThat(promotional).isLessThan(price);
    assertThat((String) read(product, "$.promotion.label")).matches("\\d+ % Rabatt");
    assertThat((String) read(product, "$.market.slug")).isEqualTo(ANATOLIA);
  }

  @Test
  void bestSellersAreTheProductsSoldMostRecentlyFromThreeProductsOn() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Bestsellermarkt");
    UUID ayran = addListing(merchant, market, "Ayran", 650);
    UUID tea = addListing(merchant, market, "Schwarzer Tee Rize", 800);
    UUID cucumber = addListing(merchant, market, "Salatgurke", 600);
    String customer = registerUser();

    orderPickup(customer, market, ayran, 3);
    orderPickup(customer, market, tea, 1);
    // Two products sold: not enough for a section, but the full list already works.
    assertThat(strings(mvc.perform(get("/api/v1/markets/" + market + "/home")).andReturn(), "$.sections[*].key")).doesNotContain("BESTSELLERS");

    orderPickup(customer, market, cucumber, 2);
    // An old order does not count (older than 30 days).
    String old = orderPickup(customer, market, tea, 5);
    jdbc.sql("UPDATE customer_order SET placed_at = placed_at - interval '31 days' WHERE id = :id").param("id", UUID.fromString(old)).update();

    MvcResult home = mvc.perform(get("/api/v1/markets/" + market + "/home").header(HttpHeaders.ACCEPT_LANGUAGE, "de")).andReturn();
    List<String> keys = read(home, "$.sections[*].key");
    assertThat(keys.indexOf("BESTSELLERS")).isEqualTo(keys.indexOf("PROMOTIONS") + 1);
    int index = keys.indexOf("BESTSELLERS");
    assertThat((String) read(home, "$.sections[" + index + "].title")).isEqualTo("Meistverkauft");
    assertThat(strings(home, "$.sections[" + index + "].items[*].id")).containsExactly(ayran.toString(), cucumber.toString(), tea.toString());

    MvcResult all = mvc.perform(get("/api/v1/markets/" + market + "/products?bestsellersOnly=true&size=2&page=0")).andExpect(status().isOk()).andReturn();
    assertThat(strings(all, "$.items[*].id")).containsExactly(ayran.toString(), cucumber.toString());
    assertThat((Integer) read(all, "$.totalItems")).isEqualTo(3);
  }
}
