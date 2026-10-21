package de.dukkan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Products carry a placeholder icon hint: their own key, else their category's. */
class ProductIconKeyIntegrationTest extends IntegrationTest {

  @Test
  void productsCarryTheirOwnIconKeyOrTheCategoryFallback() throws Exception {
    UUID apples = listingId(ANATOLIA, "Äpfel Elstar");
    mvc.perform(get("/api/v1/products/" + apples)).andExpect(status().isOk()).andExpect(jsonPath("$.iconKey").value("apple"));

    String token = registerUser();
    setQuantity(token, marketId(ANATOLIA), apples, 1);
    mvc.perform(auth(get("/api/v1/markets/" + marketId(ANATOLIA) + "/cart"), token))
        .andExpect(jsonPath("$.items[0].iconKey").value("apple"));

    // A merchant's private product has no own key and falls back to its category's icon.
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Icon Testmarkt");
    UUID category = jdbc.sql("SELECT id FROM category WHERE slug = 'bakery'").query(UUID.class).single();
    String body =
        "{\"newProduct\":{\"name\":{\"de\":\"Hausbrot Spezial\"},\"unitSize\":\"750 g\",\"categoryId\":\""
            + category
            + "\"},\"price\":{\"amountCents\":349,\"currency\":\"EUR\"}}";
    mvc.perform(json(auth(post("/api/v1/merchant/markets/" + market + "/listings"), merchant), body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.iconKey").value("bread"));
  }
}
