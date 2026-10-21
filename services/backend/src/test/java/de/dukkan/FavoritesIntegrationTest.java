package de.dukkan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.support.IntegrationTest;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

class FavoritesIntegrationTest extends IntegrationTest {

  @Test
  void favouriteMarketsAreIdempotentAndListed() throws Exception {
    String token = registerUser();
    UUID anatolia = marketId(ANATOLIA);
    UUID bakery = marketId(BAKERY);
    mvc.perform(auth(put("/api/v1/favorites/markets/" + anatolia), token)).andExpect(status().isNoContent());
    mvc.perform(auth(put("/api/v1/favorites/markets/" + anatolia), token)).andExpect(status().isNoContent());
    clock.advance(Duration.ofSeconds(1));
    mvc.perform(auth(put("/api/v1/favorites/markets/" + bakery), token)).andExpect(status().isNoContent());
    mvc.perform(auth(put("/api/v1/favorites/markets/" + UUID.randomUUID()), token)).andExpect(status().isNotFound());

    MvcResult list =
        mvc.perform(auth(get("/api/v1/favorites/markets").param("lat", String.valueOf(ST_GEORG_LAT)).param("lng", String.valueOf(ST_GEORG_LNG)), token))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(strings(list, "$[*].slug")).containsExactly(BAKERY, ANATOLIA);
    assertThat(ints(list, "$[*].distanceMeters")).doesNotContainNull();

    mvc.perform(auth(delete("/api/v1/favorites/markets/" + bakery), token)).andExpect(status().isNoContent());
    mvc.perform(auth(delete("/api/v1/favorites/markets/" + bakery), token)).andExpect(status().isNoContent());
    assertThat(strings(mvc.perform(auth(get("/api/v1/favorites/markets"), token)).andReturn(), "$[*].slug"))
        .containsExactly(ANATOLIA);
  }

  @Test
  void manualAndFrequentlyOrderedProductsAreGroupedByCategory() throws Exception {
    String token = registerUser();
    UUID bakery = marketId(BAKERY);
    UUID franzbroetchen = listingId(BAKERY, "Franzbrötchen");
    UUID milk = listingId(BAKERY, "Frische Vollmilch 3,5 %");
    UUID honey = listingId(BAKERY, "Blütenhonig aus Hamburg");

    mvc.perform(auth(put("/api/v1/favorites/products/" + honey), token)).andExpect(status().isNoContent());
    mvc.perform(auth(put("/api/v1/favorites/products/" + honey), token)).andExpect(status().isNoContent());
    mvc.perform(auth(put("/api/v1/favorites/products/" + UUID.randomUUID()), token)).andExpect(status().isNotFound());

    // Two orders with Franzbrötchen make it "frequently ordered"; milk only once does not.
    orderPickup(token, bakery, franzbroetchen, 4);
    setQuantity(token, bakery, milk, 1);
    orderPickup(token, bakery, franzbroetchen, 4);

    MvcResult groups =
        mvc.perform(auth(get("/api/v1/markets/" + bakery + "/favorites"), token).header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(strings(groups, "$[*].category.slug")).containsExactly("bakery", "deli");
    assertThat(strings(groups, "$[0].items[*].id")).containsExactly(franzbroetchen.toString());
    assertThat(strings(groups, "$[0].items[*].favoriteReason")).containsExactly("FREQUENTLY_ORDERED");
    assertThat(strings(groups, "$[1].items[*].favoriteReason")).containsExactly("MANUAL");
    assertThat(strings(groups, "$[1].items[*].name")).containsExactly("Hamburg Wildflower Honey");
    assertThat(booleans(groups, "$[1].items[*].favorite")).containsExactly(true);
    assertThat(strings(groups, "$[*].category.name")).containsExactly("Bread & Bakery", "Deli & Preserves");

    // The product flag and the home section follow.
    mvc.perform(auth(get("/api/v1/products/" + honey), token)).andExpect(status().isOk())
        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.favorite").value(true));
    MvcResult home = mvc.perform(auth(get("/api/v1/markets/" + bakery + "/home"), token)).andReturn();
    assertThat(strings(home, "$.sections[?(@.key == 'FREQUENTLY_ORDERED')].items[*].id")).containsExactly(franzbroetchen.toString());

    mvc.perform(auth(delete("/api/v1/favorites/products/" + honey), token)).andExpect(status().isNoContent());
    MvcResult after = mvc.perform(auth(get("/api/v1/markets/" + bakery + "/favorites"), token)).andReturn();
    assertThat(strings(after, "$[*].category.slug")).containsExactly("bakery");

    // Other users have no favourites; anonymous callers get 401.
    String other = registerUser();
    assertThat(objects(mvc.perform(auth(get("/api/v1/markets/" + bakery + "/favorites"), other)).andReturn(), "$")).isEmpty();
    mvc.perform(get("/api/v1/markets/" + bakery + "/favorites")).andExpect(status().isUnauthorized());
  }

  @Test
  void cancelledOrdersDoNotCountAsFrequentlyOrdered() throws Exception {
    String token = registerUser();
    UUID bakery = marketId(BAKERY);
    UUID pretzel = listingId(BAKERY, "Laugenbrezel");
    orderPickup(token, bakery, pretzel, 8);
    String second = orderPickup(token, bakery, pretzel, 8);
    mvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/orders/" + second + "/cancel"), token))
        .andExpect(status().isOk());
    assertThat(objects(mvc.perform(auth(get("/api/v1/markets/" + bakery + "/favorites"), token)).andReturn(), "$")).isEmpty();
  }
}
