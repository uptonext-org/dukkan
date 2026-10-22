package de.dukkan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** B-51: data export and account deletion (GDPR Art. 15, 17, 20; store requirement). */
class AccountPrivacyIntegrationTest extends IntegrationTest {

  private void complete(String merchant, String order) throws Exception {
    for (String status : List.of("ACCEPTED", "READY_FOR_PICKUP", "COMPLETED")) {
      mvc.perform(json(auth(post("/api/v1/merchant/orders/" + order + "/status"), merchant), toJson(Map.of("status", status))))
          .andExpect(status().isOk());
    }
  }

  @Test
  void exportShowsEverythingThenDeletionLeavesOnlyAnonymousOrders() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Löschmarkt");
    UUID listing = addListing(merchant, market, "Ayran", 650);
    String email = uniqueEmail();
    String customer = register(email, "CUSTOMER");
    UUID customerId = userId(customer);
    createAddress(customer, "Lange Reihe", "12", "20099");
    mvc.perform(auth(put("/api/v1/favorites/markets/" + market), customer)).andExpect(status().is2xxSuccessful());
    String order = orderPickup(customer, market, listing, 1);
    complete(merchant, order);
    setQuantity(customer, market, listing, 2); // an open cart
    mvc.perform(json(auth(put("/api/v1/me/devices"), customer), toJson(Map.of("token", "fcm-" + customerId, "platform", "IOS"))))
        .andExpect(status().isNoContent());
    String refresh =
        read(mvc.perform(json(post("/api/v1/auth/login"), toJson(Map.of("email", email, "password", PASSWORD)))).andReturn(), "$.refreshToken");

    MvcResult export =
        mvc.perform(auth(get("/api/v1/me/export"), customer))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"dukkan-daten.json\""))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.account.email").value(email))
            .andExpect(jsonPath("$.addresses[0].street").value("Lange Reihe"))
            .andExpect(jsonPath("$.orders[0].id").value(order))
            .andExpect(jsonPath("$.favorites.marketIds[0]").value(market.toString()))
            .andExpect(jsonPath("$.pushEnabled").value(true))
            .andReturn();
    ProductDraftIntegrationTest.conforms("AccountExport", object(export, "$"));

    // Wrong password: nothing happens.
    mvc.perform(json(auth(delete("/api/v1/me"), customer), "{\"password\":\"wrong-password\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("password"))
        .andExpect(jsonPath("$.errors[0].code").value("Incorrect"));
    mvc.perform(json(auth(delete("/api/v1/me"), customer), toJson(Map.of("password", PASSWORD)))).andExpect(status().isNoContent());

    // The access token has not expired yet, but it no longer works: no new cart, no profile.
    mvc.perform(auth(get("/api/v1/me"), customer)).andExpect(status().isUnauthorized());
    mvc.perform(json(auth(put("/api/v1/markets/" + market + "/cart/items/" + listing), customer), "{\"quantity\":1}"))
        .andExpect(status().isUnauthorized());

    // No sign-in, no session, no personal rows; the email is free again.
    mvc.perform(json(post("/api/v1/auth/login"), toJson(Map.of("email", email, "password", PASSWORD)))).andExpect(status().isUnauthorized());
    mvc.perform(json(post("/api/v1/auth/refresh"), toJson(Map.of("refreshToken", refresh)))).andExpect(status().isUnauthorized());
    for (String table : List.of("address", "cart", "favorite_market", "device_token", "notification", "refresh_token")) {
      assertThat(jdbc.sql("SELECT count(*) FROM " + table + " WHERE user_id = :u").param("u", customerId).query(Long.class).single()).as(table).isZero();
    }
    Map<String, Object> user = jdbc.sql("SELECT email, first_name, last_name FROM app_user WHERE id = :u").param("u", customerId).query().singleRow();
    assertThat(user.get("email").toString()).isEqualTo("deleted-%s@deleted.invalid", customerId);
    assertThat(user.get("first_name") + " " + user.get("last_name")).isEqualTo("Gelöschtes Konto");
    // The market keeps the order for its records, without the customer's name.
    MvcResult orders = mvc.perform(auth(get("/api/v1/merchant/markets/" + market + "/orders"), merchant)).andReturn();
    assertThat(strings(orders, "$[*].customerName")).containsExactly("Gelöschtes Konto");
    assertThat(strings(orders, "$[*].orderNumber")).hasSize(1);
    register(email, "CUSTOMER");
  }

  @Test
  void openOrdersAndOwnMarketsBlockTheDeletion() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Offenmarkt");
    UUID listing = addListing(merchant, market, "Ayran", 650);
    String customer = registerUser();
    String order = orderPickup(customer, market, listing, 1);
    mvc.perform(json(auth(delete("/api/v1/me"), customer), toJson(Map.of("password", PASSWORD))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ACCOUNT_HAS_OPEN_ORDERS"));
    mvc.perform(auth(post("/api/v1/orders/" + order + "/cancel"), customer)).andExpect(status().isOk());
    mvc.perform(json(auth(delete("/api/v1/me"), customer), toJson(Map.of("password", PASSWORD)))).andExpect(status().isNoContent());

    mvc.perform(json(auth(delete("/api/v1/me"), merchant), toJson(Map.of("password", PASSWORD))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ACCOUNT_OWNS_MARKETS"));
    mvc.perform(delete("/api/v1/me")).andExpect(status().isUnauthorized());
  }
}
