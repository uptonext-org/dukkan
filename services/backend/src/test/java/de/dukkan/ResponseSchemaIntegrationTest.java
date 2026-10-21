package de.dukkan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.market.application.MarketService;
import de.dukkan.support.IntegrationTest;
import de.dukkan.support.OpenApiContract;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Real responses contain every property that the contract marks as required (following {@code
 * allOf}), so the generated client and the backend agree on field names.
 */
class ResponseSchemaIntegrationTest extends IntegrationTest {

  private static final OpenApiContract CONTRACT = OpenApiContract.load();

  @Autowired private MarketService markets;

  private static void conforms(String schema, Object node) {
    assertThat(node).as(schema).isInstanceOf(Map.class);
    List<String> keys = ((Map<?, ?>) node).keySet().stream().map(String::valueOf).toList();
    assertThat(keys).as(schema).containsAll(CONTRACT.requiredFields(schema));
  }

  private Object first(MvcResult result, String path) throws Exception {
    Object value = read(result, path);
    return value instanceof List<?> list ? list.getFirst() : value;
  }

  @Test
  void customerResponsesMatchTheContract() throws Exception {
    String token = registerUser();
    UUID anatolia = marketId(ANATOLIA);
    UUID bakery = marketId(BAKERY);

    conforms("User", read(mvc.perform(auth(get("/api/v1/me"), token)).andReturn(), "$"));
    conforms("Locale", first(mvc.perform(get("/api/v1/locales")).andReturn(), "$"));
    conforms("CodeName", first(mvc.perform(get("/api/v1/cuisines")).andReturn(), "$"));
    conforms("Place", first(mvc.perform(get("/api/v1/geo/search").param("q", "Lange Reihe")).andReturn(), "$"));

    MvcResult page = mvc.perform(get("/api/v1/markets").param("lat", "53.5551").param("lng", "10.0128")).andReturn();
    conforms("MarketPage", read(page, "$"));
    conforms("MarketSummary", first(page, "$.items"));
    conforms("FulfilmentOptions", first(page, "$.items[0].fulfilment"));
    MvcResult detail = mvc.perform(get("/api/v1/markets/" + anatolia)).andReturn();
    conforms("MarketDetail", read(detail, "$"));
    conforms("OpeningHours", first(detail, "$.openingHours"));

    MvcResult home = mvc.perform(get("/api/v1/markets/" + anatolia + "/home")).andReturn();
    conforms("MarketHome", read(home, "$"));
    conforms("Banner", first(home, "$.banners"));
    conforms("HomeSection", first(home, "$.sections"));
    conforms("ProductSummary", first(home, "$.sections[0].items"));
    conforms("PromotionBadge", first(home, "$.sections[0].items[0].promotion"));
    conforms("Category", first(mvc.perform(get("/api/v1/markets/" + anatolia + "/categories")).andReturn(), "$"));
    conforms("ProductPage", read(mvc.perform(get("/api/v1/markets/" + anatolia + "/products")).andReturn(), "$"));
    conforms("Promotion", first(mvc.perform(get("/api/v1/markets/" + anatolia + "/promotions")).andReturn(), "$"));
    UUID apples = listingId(ANATOLIA, "Äpfel Elstar");
    MvcResult product = mvc.perform(get("/api/v1/products/" + apples)).andReturn();
    conforms("ProductDetail", read(product, "$"));
    conforms("UnitPrice", read(product, "$.comparisonPrice"));
    MvcResult offers =
        mvc.perform(get("/api/v1/search/products").param("q", "apfel").param("lat", "53.5551").param("lng", "10.0128")).andReturn();
    conforms("MarketOfferPage", read(offers, "$"));
    conforms("MarketOffer", first(offers, "$.items"));
    conforms("Slot", first(mvc.perform(get("/api/v1/markets/" + bakery + "/slots").param("type", "PICKUP")).andReturn(), "$"));

    String address = createAddress(token, "Pfefferminzgang", "6", "20099");
    conforms("Address", first(mvc.perform(auth(get("/api/v1/addresses"), token)).andReturn(), "$"));
    setQuantity(token, anatolia, listingId(ANATOLIA, "Salatgurke"), 24);
    MvcResult cart =
        reserve(token, anatolia, "DELIVERY", availableSlot(slots(token, anatolia, "DELIVERY", address), 1), address);
    conforms("Cart", read(cart, "$"));
    conforms("CartItem", first(cart, "$.items"));
    conforms("FulfilmentReservation", read(cart, "$.fulfilment"));
    conforms("CartSummary", first(mvc.perform(auth(get("/api/v1/carts"), token)).andReturn(), "$"));
    MvcResult order = placeOrder(token, anatolia);
    assertThat(order.getResponse().getStatus()).isEqualTo(201);
    conforms("Order", read(order, "$"));
    conforms("OrderItem", first(order, "$.items"));

    mvc.perform(auth(put("/api/v1/favorites/products/" + apples), token)).andExpect(status().isNoContent());
    MvcResult favorites = mvc.perform(auth(get("/api/v1/markets/" + anatolia + "/favorites"), token)).andReturn();
    conforms("FavoriteGroup", first(favorites, "$"));
    assertThat(maps(favorites, "$[0].items").getFirst()).containsKeys("favoriteReason", "id", "name", "price", "isNew");
  }

  @Test
  void merchantResponsesMatchTheContract() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Schema Testmarkt");
    addListing(merchant, market, "Ayran", 150);
    markets.assignOwner(market, userId(merchant));

    conforms("MerchantMarket", first(mvc.perform(auth(get("/api/v1/merchant/markets"), merchant)).andReturn(), "$"));
    MvcResult catalog = mvc.perform(auth(get("/api/v1/merchant/catalog").param("q", "tee"), merchant)).andReturn();
    conforms("CatalogProductPage", read(catalog, "$"));
    conforms("CatalogProduct", first(catalog, "$.items"));
    MvcResult listings = mvc.perform(auth(get("/api/v1/merchant/markets/" + market + "/listings"), merchant)).andReturn();
    conforms("ListingPage", read(listings, "$"));
    conforms("Listing", first(listings, "$.items"));
  }
}
