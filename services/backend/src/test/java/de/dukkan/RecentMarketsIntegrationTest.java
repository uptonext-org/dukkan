package de.dukkan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.support.IntegrationTest;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

/** "Meine Märkte": markets ordered from with what was bought there, then visited ones. */
class RecentMarketsIntegrationTest extends IntegrationTest {

  @Test
  void orderedMarketsComeFirstWithWhatWasBoughtThenVisitedOnes() throws Exception {
    String merchant = registerMerchant();
    UUID first = createMarket(merchant, "Erster Stammmarkt");
    UUID second = createMarket(merchant, "Zweiter Stammmarkt");
    UUID visitedOnly = createMarket(merchant, "Nur besucht");
    UUID ayran = addListing(merchant, first, "Ayran", 650);
    UUID tea = addListing(merchant, first, "Schwarzer Tee Rize", 800);
    UUID cucumber = addListing(merchant, second, "Salatgurke", 600);
    String customer = registerUser();

    orderPickup(customer, first, ayran, 1);
    String cancelled = orderPickup(customer, second, cucumber, 1);
    mvc.perform(auth(post("/api/v1/orders/" + cancelled + "/cancel"), customer)).andExpect(status().isOk());
    clock.advance(Duration.ofMinutes(5));
    String latest = orderPickup(customer, first, tea, 1);
    // Sold out since: not offered again.
    mvc.perform(json(auth(put("/api/v1/merchant/markets/" + first + "/listings/" + ayran), merchant), toJson(Map.of("available", false))))
        .andExpect(status().isOk());

    MvcResult result =
        mvc.perform(
                auth(get("/api/v1/markets/recent").param("visited", visitedOnly + "," + first + "," + UUID.randomUUID()), customer)
                    .param("lat", "48.1374")
                    .param("lng", "11.5755")
                    .header(HttpHeaders.ACCEPT_LANGUAGE, "de"))
            .andExpect(status().isOk())
            .andReturn();
    // The cancelled order does not make "second" one of my markets; unknown ids are skipped.
    assertThat(strings(result, "$[*].market.id")).containsExactly(first.toString(), visitedOnly.toString());
    assertThat((String) read(result, "$[0].lastOrderId")).isEqualTo(latest);
    assertThat((Integer) read(result, "$[0].orderCount")).isEqualTo(2);
    assertThat(strings(result, "$[0].recentProducts[*].id")).containsExactly(tea.toString());
    assertThat((Integer) read(result, "$[0].market.distanceMeters")).isNotNull();
    assertThat(object(result, "$[1].lastOrderAt")).isNull();
    assertThat(objects(result, "$[1].recentProducts")).isEmpty();
    ProductDraftIntegrationTest.conforms("RecentMarket", object(result, "$[0]"));

    // Guests get the visited markets only.
    MvcResult guest = mvc.perform(get("/api/v1/markets/recent").param("visited", first.toString())).andExpect(status().isOk()).andReturn();
    assertThat(strings(guest, "$[*].market.id")).containsExactly(first.toString());
    assertThat((Integer) read(guest, "$[0].orderCount")).isZero();
    mvc.perform(get("/api/v1/markets/recent").param("lat", "48.1")).andExpect(status().isBadRequest());
  }
}
