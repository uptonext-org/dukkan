package de.dukkan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.support.IntegrationTest;
import de.dukkan.support.MutableClock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** What a merchant controls about ordering and what customers see of it (test clock: Monday 10:00 Berlin). */
class MarketOrderSettingsIntegrationTest extends IntegrationTest {

  private UUID newMarket(String merchant, String name) throws Exception {
    MvcResult created =
        mvc.perform(json(auth(post("/api/v1/merchant/markets"), merchant), toJson(marketProfile(name))))
            .andExpect(status().isCreated())
            .andReturn();
    return UUID.fromString(read(created, "$.id"));
  }

  private static List<LocalDate> days(List<Map<String, Object>> slots) {
    return slots.stream()
        .map(s -> ZonedDateTime.parse((String) s.get("startsAt")).withZoneSameInstant(MutableClock.BERLIN).toLocalDate())
        .distinct()
        .toList();
  }

  private static List<Integer> startHours(List<Map<String, Object>> slots) {
    return slots.stream()
        .map(s -> ZonedDateTime.parse((String) s.get("startsAt")).withZoneSameInstant(MutableClock.BERLIN).getHour())
        .distinct()
        .sorted()
        .toList();
  }

  @Test
  void aNewMarketIsAShowcaseUntilTheMerchantSwitchesOrdersOn() throws Exception {
    String merchant = registerMerchant();
    UUID market = newMarket(merchant, "Schaufenster Markt");
    UUID ayran = addListing(merchant, market, "Ayran", 99);
    String customer = registerUser();

    mvc.perform(auth(get("/api/v1/merchant/markets/" + market + "/order-settings"), merchant))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ordersEnabled").value(false))
        .andExpect(jsonPath("$.paymentMethods[0]").value("CASH"))
        .andExpect(jsonPath("$.defaults.pickupCapacity").value(10))
        .andExpect(jsonPath("$.defaults.daysAhead").value(7));
    // Visible with its products, but nothing to order and no notice.
    mvc.perform(get("/api/v1/markets/" + market))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.acceptsOrders").value(false))
        .andExpect(jsonPath("$.ordersPausedUntil").doesNotExist())
        .andExpect(jsonPath("$.closedFrom").doesNotExist());
    assertThat(strings(mvc.perform(get("/api/v1/markets").param("lat", "48.1374").param("lng", "11.5755").param("size", "50")).andReturn(), "$.items[*].id"))
        .contains(market.toString());
    mvc.perform(get("/api/v1/products/" + ayran)).andExpect(status().isOk());
    assertThat(slots(null, market, "PICKUP", null)).isEmpty();
    MvcResult refused = setQuantity(customer, market, ayran, 1);
    assertThat(refused.getResponse().getStatus()).isEqualTo(422);
    assertThat((String) read(refused, "$.code")).isEqualTo("ORDERS_NOT_ACCEPTED");

    updateOrderSettings(merchant, market, Map.of());
    mvc.perform(get("/api/v1/markets/" + market)).andExpect(jsonPath("$.acceptsOrders").value(true));
    assertThat(slots(null, market, "PICKUP", null)).isNotEmpty();
    assertThat(setQuantity(customer, market, ayran, 1).getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  void aPauseStopsNewOrdersUntilItEndsAndCustomersSeeWhen() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Pausen Markt");
    UUID ayran = addListing(merchant, market, "Ayran", 99);
    String customer = registerUser();
    confirmEmail(customer);
    setQuantity(customer, market, ayran, 1);
    String slot = availableSlot(slots(null, market, "PICKUP", null), 1);

    String until = clock.instant().plus(Duration.ofHours(20)).toString();
    updateOrderSettings(merchant, market, Map.of("pausedUntil", until));
    mvc.perform(get("/api/v1/markets/" + market))
        .andExpect(jsonPath("$.acceptsOrders").value(false))
        .andExpect(jsonPath("$.ordersPausedUntil").exists());
    assertThat(slots(null, market, "PICKUP", null)).isEmpty();
    assertThat((String) read(reserve(customer, market, "PICKUP", slot, null), "$.code")).isEqualTo("ORDERS_NOT_ACCEPTED");
    assertThat((String) read(setQuantity(customer, market, ayran, 2), "$.code")).isEqualTo("ORDERS_NOT_ACCEPTED");
    // Removing is still possible.
    assertThat(setQuantity(customer, market, ayran, 0).getResponse().getStatus()).isEqualTo(200);

    clock.advance(Duration.ofHours(21));
    mvc.perform(get("/api/v1/markets/" + market))
        .andExpect(jsonPath("$.acceptsOrders").value(true))
        .andExpect(jsonPath("$.ordersPausedUntil").doesNotExist());
    assertThat(slots(null, market, "PICKUP", null)).isNotEmpty();

    // A pause that already ended is not stored.
    MvcResult past = updateOrderSettings(merchant, market, Map.of("pausedUntil", clock.instant().minusSeconds(60).toString()));
    assertThat(object(past, "$.pausedUntil")).isNull();
  }

  @Test
  void holidaysHaveNoSlotsAndAreAnnounced() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Urlaubs Markt");
    LocalDate today = LocalDate.ofInstant(clock.instant(), MutableClock.BERLIN);

    updateOrderSettings(
        merchant, market, Map.of("closedFrom", today.plusDays(1).toString(), "closedUntil", today.plusDays(2).toString()));

    mvc.perform(get("/api/v1/markets/" + market))
        .andExpect(jsonPath("$.acceptsOrders").value(true))
        .andExpect(jsonPath("$.closedFrom").value(today.plusDays(1).toString()))
        .andExpect(jsonPath("$.closedUntil").value(today.plusDays(2).toString()));
    assertThat(days(slots(null, market, "PICKUP", null))).contains(today, today.plusDays(3)).doesNotContain(today.plusDays(1), today.plusDays(2));

    clock.advance(Duration.ofDays(3));
    mvc.perform(get("/api/v1/markets/" + market)).andExpect(jsonPath("$.closedFrom").doesNotExist());

    mvc.perform(
            json(
                auth(put("/api/v1/merchant/markets/" + market + "/order-settings"), merchant),
                toJson(orderSettings(Map.of("closedFrom", today.toString())))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("ClosedRange"));
  }

  @Test
  void capacityLeadTimeDeliveryHoursAndHorizonShapeTheSlots() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Takt Markt");
    // Slots generated with the platform defaults first: changes apply to them too.
    assertThat(slots(null, market, "PICKUP", null).getFirst().get("remainingCapacity")).isEqualTo(10);

    updateOrderSettings(
        merchant,
        market,
        Map.of(
            "pickupCapacity", 2,
            "pickupLeadMinutes", 0,
            "daysAhead", 2,
            "deliveryHours", List.of(Map.of("dayOfWeek", 2, "opensAt", "10:00", "closesAt", "12:00"))));

    List<Map<String, Object>> pickup = slots(null, market, "PICKUP", null);
    assertThat(pickup).allSatisfy(s -> assertThat(s.get("remainingCapacity")).isEqualTo(2));
    LocalDate today = LocalDate.ofInstant(clock.instant(), MutableClock.BERLIN);
    assertThat(days(pickup)).containsExactly(today, today.plusDays(1));
    // No lead time: the 11:00 slot can still be booked at 10:00 (default: one hour before).
    assertThat(pickup.getFirst().get("available")).isEqualTo(true);

    List<Map<String, Object>> delivery = slots(null, market, "DELIVERY", null);
    assertThat(days(delivery)).containsExactly(today.plusDays(1));
    assertThat(startHours(delivery)).containsExactly(10, 11);

    mvc.perform(auth(get("/api/v1/merchant/markets/" + market + "/order-settings"), merchant))
        .andExpect(jsonPath("$.pickupCapacity").value(2))
        .andExpect(jsonPath("$.deliveryCapacity").doesNotExist())
        .andExpect(jsonPath("$.deliveryHours[0].opensAt").value("10:00"));
  }

  @Test
  void autoAcceptPaymentMethodsAndDefaultMaximum() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Automatik Markt");
    updateOrderSettings(merchant, market, Map.of("autoAccept", true, "paymentMethods", List.of("CASH", "CARD"), "defaultMaxOrderQuantity", 5));
    UUID ayran = addListing(merchant, market, "Ayran", 299);

    mvc.perform(get("/api/v1/markets/" + market)).andExpect(jsonPath("$.paymentMethods").value(org.hamcrest.Matchers.contains("CASH", "CARD")));
    mvc.perform(get("/api/v1/products/" + ayran)).andExpect(jsonPath("$.maxOrderQuantity").value(5));

    String customer = registerUser();
    confirmEmail(customer);
    String order = orderPickup(customer, market, ayran, 2);
    mvc.perform(auth(get("/api/v1/orders/" + order), customer)).andExpect(jsonPath("$.status").value("ACCEPTED"));
  }

  @Test
  void onlyTheOwnerSeesAndChangesTheSettings() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Eigener Markt");
    String other = registerMerchant();
    mvc.perform(auth(get("/api/v1/merchant/markets/" + market + "/order-settings"), other)).andExpect(status().isNotFound());
    mvc.perform(json(auth(put("/api/v1/merchant/markets/" + market + "/order-settings"), other), toJson(orderSettings(Map.of()))))
        .andExpect(status().isNotFound());
    mvc.perform(json(auth(put("/api/v1/merchant/markets/" + market + "/order-settings"), merchant), toJson(orderSettings(Map.of("paymentMethods", List.of())))))
        .andExpect(status().isBadRequest());
  }
}
