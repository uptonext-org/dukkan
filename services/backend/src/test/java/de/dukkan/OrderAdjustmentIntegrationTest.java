package de.dukkan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.shared.events.OutboxPublisher;
import de.dukkan.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** Missing items while packing: the market hands over fewer units, totals follow, the customer is told. */
class OrderAdjustmentIntegrationTest extends IntegrationTest {

  @Autowired private OutboxPublisher publisher;

  private ResultActions adjust(String merchant, String order, Map<Integer, Integer> units) throws Exception {
    List<Map<String, Integer>> items = units.entrySet().stream().map(e -> Map.of("lineNo", e.getKey(), "fulfilledQuantity", e.getValue())).toList();
    return mvc.perform(json(auth(put("/api/v1/merchant/orders/" + order + "/items"), merchant), toJson(Map.of("items", items))));
  }

  private void advance(String merchant, String order, String status) throws Exception {
    mvc.perform(json(auth(post("/api/v1/merchant/orders/" + order + "/status"), merchant), toJson(Map.of("status", status))))
        .andExpect(status().isOk());
  }

  @Test
  void missingItemsLowerTheTotalAndTheCustomerIsTold() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Packmarkt");
    UUID ayran = addListing(merchant, market, "Ayran", 650);
    UUID tea = addListing(merchant, market, "Schwarzer Tee Rize", 800);
    String customer = registerUser();
    setQuantity(customer, market, tea, 1);
    String order = orderPickup(customer, market, ayran, 3);

    MvcResult placed = mvc.perform(auth(get("/api/v1/orders/" + order), customer)).andExpect(status().isOk()).andReturn();
    assertThat((Integer) read(placed, "$.total.amountCents")).isEqualTo(2750);
    assertThat(object(placed, "$.originalTotal")).isNull();
    int ayranLine = ints(placed, "$.items[?(@.productId == '" + ayran + "')].lineNo").getFirst();
    int teaLine = ints(placed, "$.items[?(@.productId == '" + tea + "')].lineNo").getFirst();
    advance(merchant, order, "ACCEPTED");

    // Only one Ayran and no tea on the shelf.
    MvcResult adjusted = adjust(merchant, order, Map.of(ayranLine, 1, teaLine, 0)).andExpect(status().isOk()).andReturn();
    assertThat((Integer) read(adjusted, "$.total.amountCents")).isEqualTo(650);
    assertThat((Integer) read(adjusted, "$.subtotal.amountCents")).isEqualTo(650);
    assertThat((Integer) read(adjusted, "$.originalTotal.amountCents")).isEqualTo(2750);
    ProductDraftIntegrationTest.conforms("Order", object(adjusted, "$"));

    MvcResult seen = mvc.perform(auth(get("/api/v1/orders/" + order), customer)).andExpect(status().isOk()).andReturn();
    assertThat(ints(seen, "$.items[?(@.productId == '" + ayran + "')].fulfilledQuantity")).containsExactly(1);
    assertThat(ints(seen, "$.items[?(@.productId == '" + ayran + "')].quantity")).containsExactly(3);
    assertThat(ints(seen, "$.items[?(@.productId == '" + tea + "')].lineTotal.amountCents")).containsExactly(0);
    assertThat((Integer) read(seen, "$.originalTotal.amountCents")).isEqualTo(2750);

    MvcResult inbox = awaitInbox(customer, 1);
    assertThat((String) read(inbox, "$.items[0].kind")).isEqualTo("ORDER_ADJUSTED");
    assertThat((String) read(inbox, "$.items[0].title")).isEqualTo("Bestellung angepasst");
    assertThat((String) read(inbox, "$.items[0].body")).contains("2 Artikel nicht (ganz) verfügbar", "6,50 €", "statt 27,50 €");

    // Found the tea after all: back to what was ordered for that line.
    MvcResult back = adjust(merchant, order, Map.of(ayranLine, 3, teaLine, 1)).andExpect(status().isOk()).andReturn();
    assertThat((Integer) read(back, "$.total.amountCents")).isEqualTo(2750);
    assertThat(object(back, "$.originalTotal")).isNull();
    assertThat(objects(back, "$.items[?(@.fulfilledQuantity != null)]")).isEmpty();
  }

  @Test
  void onlyTheOwnerAndOnlyWhilePackingWithSomethingLeft() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Grenzmarkt");
    UUID ayran = addListing(merchant, market, "Ayran", 650);
    String customer = registerUser();
    String order = orderPickup(customer, market, ayran, 2);

    adjust(merchant, order, Map.of(2, 1)).andExpect(status().isBadRequest());
    adjust(merchant, order, Map.of(1, 3)).andExpect(status().isBadRequest());
    adjust(merchant, order, Map.of(1, 0))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("ORDER_ADJUSTMENT_EMPTY"));
    adjust(registerMerchant(), order, Map.of(1, 1)).andExpect(status().isNotFound());
    mvc.perform(json(auth(put("/api/v1/merchant/orders/" + order + "/items"), merchant), "{\"items\":[{\"lineNo\":1,\"fulfilledQuantity\":1},{\"lineNo\":1,\"fulfilledQuantity\":2}]}"))
        .andExpect(status().isBadRequest());

    // Placed orders can be adjusted too (before accepting) …
    adjust(merchant, order, Map.of(1, 1)).andExpect(status().isOk()).andExpect(jsonPath("$.total.amountCents").value(650));
    advance(merchant, order, "ACCEPTED");
    advance(merchant, order, "READY_FOR_PICKUP");
    // … but not once the bag is ready.
    adjust(merchant, order, Map.of(1, 2))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("ORDER_NOT_ADJUSTABLE"));
  }

  private MvcResult awaitInbox(String token, int count) throws Exception {
    for (int i = 0; i < 50; i++) {
      publisher.publishPending();
      MvcResult result = mvc.perform(auth(get("/api/v1/me/notifications"), token)).andExpect(status().isOk()).andReturn();
      if (((Integer) read(result, "$.totalItems")) >= count) {
        return result;
      }
      Thread.sleep(100);
    }
    throw new AssertionError("Expected " + count + " notification(s)");
  }
}
