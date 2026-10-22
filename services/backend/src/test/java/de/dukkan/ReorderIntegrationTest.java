package de.dukkan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.support.IntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

/** Ordering a past order again: available products go into the cart, the rest is reported. */
class ReorderIntegrationTest extends IntegrationTest {

  private MvcResult reorder(String token, String orderId) throws Exception {
    return mvc.perform(auth(post("/api/v1/orders/" + orderId + "/reorder"), token).header(HttpHeaders.ACCEPT_LANGUAGE, "de")).andReturn();
  }

  private int cartQuantity(String token, UUID market, UUID listing) throws Exception {
    MvcResult cart = mvc.perform(auth(get("/api/v1/markets/" + market + "/cart"), token)).andExpect(status().isOk()).andReturn();
    return ints(cart, "$.items[?(@.productId == '" + listing + "')].quantity").stream().findFirst().orElse(0);
  }

  @Test
  void availableProductsGoIntoTheCartAndTheRestIsReported() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Nachbestellmarkt");
    UUID ayran = addListing(merchant, market, "Ayran", 300);
    UUID tea = addListing(merchant, market, "Schwarzer Tee Rize", 800);
    UUID cucumber = addListing(merchant, market, "Salatgurke", 100);
    String customer = registerUser();

    setQuantity(customer, market, ayran, 2);
    setQuantity(customer, market, tea, 1);
    String order = orderPickup(customer, market, cucumber, 1);

    // Since then: tea sold out, cucumber no longer sold, at most 3 ayran per order.
    String base = "/api/v1/merchant/markets/" + market + "/listings/";
    mvc.perform(json(auth(put(base + tea), merchant), toJson(Map.of("available", false)))).andExpect(status().isOk());
    mvc.perform(auth(delete(base + cucumber), merchant)).andExpect(status().isNoContent());
    mvc.perform(json(auth(put(base + ayran), merchant), toJson(Map.of("maxOrderQuantity", 3)))).andExpect(status().isOk());

    MvcResult first = reorder(customer, order);
    assertThat(first.getResponse().getStatus()).isEqualTo(200);
    assertThat((String) read(first, "$.marketId")).isEqualTo(market.toString());
    assertThat((Integer) read(first, "$.addedProducts")).isEqualTo(1);
    assertThat(maps(first, "$.skipped"))
        .containsExactlyInAnyOrder(
            Map.of("productId", tea.toString(), "name", "Schwarzer Tee Rize", "reason", "UNAVAILABLE"),
            Map.of("productId", cucumber.toString(), "name", "Salatgurke", "reason", "REMOVED"));
    ProductDraftIntegrationTest.conforms("ReorderResult", object(first, "$"));
    assertThat(cartQuantity(customer, market, ayran)).isEqualTo(2);

    // Again: 2 + 2 is capped at 3; once full, nothing more is added.
    MvcResult second = reorder(customer, order);
    assertThat((Integer) read(second, "$.addedProducts")).isEqualTo(1);
    assertThat(strings(second, "$.skipped[?(@.productId == '" + ayran + "')].reason")).containsExactly("LIMITED");
    assertThat(cartQuantity(customer, market, ayran)).isEqualTo(3);
    assertThat((Integer) read(reorder(customer, order), "$.addedProducts")).isZero();

    // Only the customer's own orders.
    mvc.perform(auth(post("/api/v1/orders/" + order + "/reorder"), registerUser())).andExpect(status().isNotFound());
    mvc.perform(post("/api/v1/orders/" + order + "/reorder")).andExpect(status().isUnauthorized());
  }
}
