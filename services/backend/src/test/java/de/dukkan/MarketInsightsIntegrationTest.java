package de.dukkan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.shared.time.DukkanTime;
import de.dukkan.support.IntegrationTest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

/** The merchant's sales overview: totals, comparison period, days, hours and best sellers. */
class MarketInsightsIntegrationTest extends IntegrationTest {

  private void movePlacedAt(String orderId, Duration back) {
    jdbc.sql("UPDATE customer_order SET placed_at = placed_at - make_interval(secs => :seconds) WHERE id = :id")
        .param("seconds", back.toSeconds())
        .param("id", UUID.fromString(orderId))
        .update();
  }

  private void setPlacedAt(String orderId, Instant placedAt) {
    jdbc.sql("UPDATE customer_order SET placed_at = :placedAt WHERE id = :id")
        .param("placedAt", Timestamp.from(placedAt))
        .param("id", UUID.fromString(orderId))
        .update();
  }

  private Timestamp placedAt(String orderId) {
    return jdbc.sql("SELECT placed_at FROM customer_order WHERE id = :id").param("id", UUID.fromString(orderId)).query(Timestamp.class).single();
  }

  @Test
  void totalsDaysHoursAndBestSellersOfTheLastDays() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Auswertungsmarkt");
    UUID ayran = addListing(merchant, market, "Ayran", 650);
    UUID tea = addListing(merchant, market, "Schwarzer Tee Rize", 800);
    String leyla = registerUser();
    String jonas = registerUser();

    String first = orderPickup(leyla, market, ayran, 2); // 13,00 €, three days ago
    String second = orderPickup(leyla, market, tea, 1); // 8,00 €, today: Leyla returns
    String third = orderPickup(jonas, market, ayran, 1); // 6,50 €, rejected
    String old = orderPickup(jonas, market, tea, 3); // 24,00 €, 40 days ago (previous period)
    mvc.perform(json(auth(post("/api/v1/merchant/orders/" + third + "/status"), merchant), toJson(Map.of("status", "REJECTED"))))
        .andExpect(status().isOk());
    movePlacedAt(first, Duration.ofDays(3));
    movePlacedAt(old, Duration.ofDays(40));

    MvcResult result =
        mvc.perform(auth(get("/api/v1/merchant/markets/" + market + "/insights?days=30"), merchant).header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totals.orders").value(2))
            .andExpect(jsonPath("$.totals.revenue.amountCents").value(2100))
            .andExpect(jsonPath("$.totals.averageOrderValue.amountCents").value(1050))
            .andExpect(jsonPath("$.previous.orders").value(1))
            .andExpect(jsonPath("$.previous.revenue.amountCents").value(2400))
            .andExpect(jsonPath("$.rejectedOrders").value(1))
            .andExpect(jsonPath("$.cancelledOrders").value(0))
            .andExpect(jsonPath("$.pickupOrders").value(2))
            .andExpect(jsonPath("$.deliveryOrders").value(0))
            .andExpect(jsonPath("$.customers").value(1))
            .andExpect(jsonPath("$.returningCustomers").value(1))
            .andExpect(jsonPath("$.topProducts[0].name").value("Ayran Yoghurt Drink"))
            .andExpect(jsonPath("$.topProducts[0].quantity").value(2))
            .andExpect(jsonPath("$.topProducts[0].revenue.amountCents").value(1300))
            .andExpect(jsonPath("$.topProducts[1].name").value("Rize Black Tea"))
            .andReturn();
    ProductDraftIntegrationTest.conforms("MarketInsights", object(result, "$"));
    ProductDraftIntegrationTest.conforms("InsightTotals", object(result, "$.totals"));

    LocalDate today = LocalDate.ofInstant(clock.instant(), DukkanTime.ZONE);
    assertThat(objects(result, "$.daily")).hasSize(30);
    assertThat((String) read(result, "$.from")).isEqualTo(today.minusDays(29).toString());
    assertThat((String) read(result, "$.to")).isEqualTo(today.toString());
    assertThat((String) read(result, "$.daily[26].date")).isEqualTo(today.minusDays(3).toString());
    assertThat(ints(result, "$.daily[*].orders").stream().mapToInt(Integer::intValue).sum()).isEqualTo(2);
    assertThat((Integer) read(result, "$.daily[26].orders")).isEqualTo(1);
    assertThat((Integer) read(result, "$.daily[29].revenue.amountCents")).isEqualTo(800);

    int hour = placedAt(second).toInstant().atZone(DukkanTime.ZONE).getHour();
    assertThat(ints(result, "$.ordersByHour")).hasSize(24);
    assertThat(ints(result, "$.ordersByHour").stream().mapToInt(Integer::intValue).sum()).isEqualTo(2);
    assertThat((Integer) read(result, "$.ordersByHour[" + hour + "]")).isPositive();

    // A shorter period leaves the order of three days ago in the comparison period.
    mvc.perform(auth(get("/api/v1/merchant/markets/" + market + "/insights?days=2"), merchant))
        .andExpect(jsonPath("$.totals.orders").value(1))
        .andExpect(jsonPath("$.previous.orders").value(1))
        .andExpect(jsonPath("$.daily.length()").value(2));
  }

  @Test
  void thePeriodBeforeEndsAtTheSameTimeOfDay() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Vergleichsmarkt");
    UUID ayran = addListing(merchant, market, "Ayran", 650);
    String customer = registerUser();
    String earlier = orderPickup(customer, market, ayran, 1);
    String later = orderPickup(customer, market, ayran, 1);
    // A week ago, one hour before and one hour after the current time of day. Local time, not
    // 168 hours: in the week after a daylight saving change the two differ by an hour.
    ZonedDateTime weekAgo = clock.instant().atZone(DukkanTime.ZONE).minusDays(7);
    setPlacedAt(earlier, weekAgo.minusHours(1).toInstant());
    setPlacedAt(later, weekAgo.plusHours(1).toInstant());

    // Today is not over, so only the order placed before this time a week ago is compared.
    mvc.perform(auth(get("/api/v1/merchant/markets/" + market + "/insights?days=7"), merchant))
        .andExpect(jsonPath("$.totals.orders").value(0))
        .andExpect(jsonPath("$.previous.orders").value(1));
  }

  @Test
  void anEmptyPeriodHasNoAverageAndOnlyTheOwnerMayLook() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Leerer Auswertungsmarkt");
    mvc.perform(auth(get("/api/v1/merchant/markets/" + market + "/insights?days=7"), merchant))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totals.orders").value(0))
        .andExpect(jsonPath("$.totals.averageOrderValue").isEmpty())
        .andExpect(jsonPath("$.topProducts").isEmpty())
        .andExpect(jsonPath("$.daily.length()").value(7));
    mvc.perform(auth(get("/api/v1/merchant/markets/" + market + "/insights?days=0"), merchant)).andExpect(status().isBadRequest());
    mvc.perform(auth(get("/api/v1/merchant/markets/" + market + "/insights"), registerMerchant())).andExpect(status().isNotFound());
    mvc.perform(auth(get("/api/v1/merchant/markets/" + market + "/insights"), registerUser())).andExpect(status().isForbidden());
  }
}
