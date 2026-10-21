package de.dukkan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.market.application.MarketService;
import de.dukkan.support.IntegrationTest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Browse → carts in two markets → delivery in one, pickup in the other → orders → merchant
 * advances the status → customer cancellation rules.
 */
class CustomerFlowIntegrationTest extends IntegrationTest {

  @Autowired private MarketService markets;

  @Test
  void fullCustomerAndMerchantFlowAcrossTwoMarkets() throws Exception {
    String customer = registerUser();
    UUID anatolia = marketId(ANATOLIA);
    UUID bakery = marketId(BAKERY);

    // Browse
    MvcResult nearby =
        mvc.perform(get("/api/v1/markets").param("lat", String.valueOf(ST_GEORG_LAT)).param("lng", String.valueOf(ST_GEORG_LNG)))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(strings(nearby, "$.items[*].id")).contains(anatolia.toString(), bakery.toString());

    // One cart per market
    UUID apples = listingId(ANATOLIA, "Äpfel Elstar");
    UUID tomatoes = listingId(ANATOLIA, "Strauchtomaten");
    UUID franzbroetchen = listingId(BAKERY, "Franzbrötchen");
    MvcResult anatoliaCart = setQuantity(customer, anatolia, apples, 5);
    assertThat(anatoliaCart.getResponse().getStatus()).isEqualTo(200);
    anatoliaCart = setQuantity(customer, anatolia, tomatoes, 3);
    assertThat((Integer) read(anatoliaCart, "$.itemCount")).isEqualTo(8);
    assertThat((Integer) read(anatoliaCart, "$.discountTotal.amountCents")).isPositive();
    assertThat((String) read(anatoliaCart, "$.market.slug")).isEqualTo(ANATOLIA);
    assertThat((Boolean) read(anatoliaCart, "$.meetsMinimumOrderValue")).isTrue();
    clock.advance(Duration.ofMinutes(1));
    MvcResult bakeryCart = setQuantity(customer, bakery, franzbroetchen, 4);
    assertThat((Integer) read(bakeryCart, "$.itemCount")).isEqualTo(4);
    // The 20 % promotion: 189 → 151 per piece.
    assertThat((Integer) read(bakeryCart, "$.items[0].lineDiscount.amountCents")).isEqualTo(4 * 38);

    MvcResult carts = mvc.perform(auth(get("/api/v1/carts"), customer)).andExpect(status().isOk()).andReturn();
    assertThat(strings(carts, "$[*].market.slug")).containsExactly(BAKERY, ANATOLIA);
    assertThat(ints(carts, "$[*].itemCount")).containsExactly(4, 8);

    // A product of another market cannot go into this market's cart.
    assertThat(setQuantity(customer, bakery, apples, 1).getResponse().getStatus()).isEqualTo(404);

    // Delivery in Anatolia (inside the 4 km radius), pickup at the bakery (pickup only).
    String home = createAddress(customer, "Pfefferminzgang", "4", "20099");
    List<Map<String, Object>> deliverySlots = slots(customer, anatolia, "DELIVERY", home);
    String deliverySlot = availableSlot(deliverySlots, 1);
    MvcResult reservedDelivery = reserve(customer, anatolia, "DELIVERY", deliverySlot, home);
    assertThat(reservedDelivery.getResponse().getStatus()).isEqualTo(200);
    assertThat((String) read(reservedDelivery, "$.fulfilment.type")).isEqualTo("DELIVERY");
    assertThat((String) read(reservedDelivery, "$.fulfilment.addressId")).isEqualTo(home);
    assertThat((Integer) read(reservedDelivery, "$.deliveryFee.amountCents")).isEqualTo(290);
    assertThat((Integer) read(reservedDelivery, "$.fulfilment.slot.fee.amountCents")).isEqualTo(290);

    // Free delivery from 40 €.
    MvcResult bigger = setQuantity(customer, anatolia, apples, 20);
    int merchandise = (Integer) read(bigger, "$.subtotal.amountCents") - (Integer) read(bigger, "$.discountTotal.amountCents");
    assertThat(merchandise).isGreaterThanOrEqualTo(4000);
    assertThat((Integer) read(bigger, "$.deliveryFee.amountCents")).isZero();
    assertThat((Integer) read(bigger, "$.total.amountCents")).isEqualTo(merchandise);

    MvcResult wrongType = reserve(customer, bakery, "DELIVERY", deliverySlot, home);
    assertThat(wrongType.getResponse().getStatus()).isEqualTo(422);
    assertThat((String) read(wrongType, "$.code")).isEqualTo("FULFILMENT_NOT_OFFERED");

    String pickupSlot = availableSlot(slots(null, bakery, "PICKUP", null), 1);
    MvcResult reservedPickup = reserve(customer, bakery, "PICKUP", pickupSlot, null);
    assertThat(reservedPickup.getResponse().getStatus()).isEqualTo(200);
    assertThat((Integer) read(reservedPickup, "$.deliveryFee.amountCents")).isZero();
    assertThat(object(reservedPickup, "$.fulfilment.addressId")).isNull();

    // Orders: each empties only its own market's cart.
    MvcResult deliveryOrder = placeOrder(customer, anatolia);
    assertThat(deliveryOrder.getResponse().getStatus()).isEqualTo(201);
    assertThat((String) read(deliveryOrder, "$.status")).isEqualTo("PLACED");
    assertThat((String) read(deliveryOrder, "$.fulfilmentType")).isEqualTo("DELIVERY");
    assertThat((String) read(deliveryOrder, "$.deliveryAddress.street")).isEqualTo("Pfefferminzgang");
    assertThat((String) read(deliveryOrder, "$.orderNumber")).matches("DK-\\d{8}-\\d{6}");
    assertThat(object(deliveryOrder, "$.customerName")).isNull();
    assertThat(objects(deliveryOrder, "$.items")).hasSize(2);
    String deliveryOrderId = read(deliveryOrder, "$.id");
    assertThat(strings(mvc.perform(auth(get("/api/v1/carts"), customer)).andReturn(), "$[*].market.slug"))
        .containsExactly(BAKERY);

    MvcResult pickupOrder =
        mvc.perform(auth(post("/api/v1/markets/" + bakery + "/orders"), customer).header("Idempotency-Key", "flow-" + UUID.randomUUID()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.fulfilmentType").value("PICKUP"))
            .andExpect(jsonPath("$.deliveryAddress").isEmpty())
            .andExpect(jsonPath("$.market.slug").value(BAKERY))
            .andReturn();
    String pickupOrderId = read(pickupOrder, "$.id");
    assertThat(objects(mvc.perform(auth(get("/api/v1/carts"), customer)).andReturn(), "$")).isEmpty();

    // Orders are listed newest first and localised.
    MvcResult orders =
        mvc.perform(auth(get("/api/v1/orders"), customer).header(HttpHeaders.ACCEPT_LANGUAGE, "ar")).andExpect(status().isOk()).andReturn();
    assertThat(strings(orders, "$[*].id")).containsExactly(pickupOrderId, deliveryOrderId);
    assertThat((String) read(orders, "$[0].items[0].name")).isEqualTo("فرانزبروتشن بالقرفة");

    // The merchant of the bakery advances the pickup order.
    String merchant = registerMerchant();
    markets.assignOwner(bakery, userId(merchant));
    advance(merchant, pickupOrderId, "OUT_FOR_DELIVERY", 422, "INVALID_STATUS_TRANSITION");
    advance(merchant, pickupOrderId, "ACCEPTED", 200, null);
    MvcResult merchantOrders =
        mvc.perform(auth(get("/api/v1/merchant/markets/" + bakery + "/orders").param("status", "ACCEPTED"), merchant))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(strings(merchantOrders, "$[*].id")).contains(pickupOrderId);
    assertThat(strings(merchantOrders, "$[?(@.id == '" + pickupOrderId + "')].customerName")).containsExactly("Test User");
    advance(merchant, pickupOrderId, "OUT_FOR_DELIVERY", 422, "INVALID_STATUS_TRANSITION");
    advance(merchant, pickupOrderId, "READY_FOR_PICKUP", 200, null);

    // READY_FOR_PICKUP can no longer be cancelled by the customer.
    mvc.perform(auth(post("/api/v1/orders/" + pickupOrderId + "/cancel"), customer))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("ORDER_NOT_CANCELLABLE"));
    advance(merchant, pickupOrderId, "COMPLETED", 200, null);
    advance(merchant, pickupOrderId, "ACCEPTED", 422, "INVALID_STATUS_TRANSITION");

    // The bakery's merchant cannot see or change the Anatolia order.
    mvc.perform(json(auth(post("/api/v1/merchant/orders/" + deliveryOrderId + "/status"), merchant), "{\"status\":\"ACCEPTED\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(auth(get("/api/v1/merchant/markets/" + anatolia + "/orders"), merchant)).andExpect(status().isNotFound());

    // The PLACED delivery order can be cancelled before the cutoff; this releases its capacity.
    int before = remaining(customer, anatolia, home, deliverySlot);
    mvc.perform(auth(post("/api/v1/orders/" + deliveryOrderId + "/cancel"), customer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
    assertThat(remaining(customer, anatolia, home, deliverySlot)).isEqualTo(before + 1);
    mvc.perform(auth(post("/api/v1/orders/" + deliveryOrderId + "/cancel"), customer))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("ORDER_NOT_CANCELLABLE"));
  }

  @Test
  void acceptedOrdersCanBeCancelledOnlyBeforeTheCutoffAndRejectionReleasesCapacity() throws Exception {
    String customer = registerUser();
    UUID bakery = marketId(BAKERY);
    String merchant = registerMerchant();
    markets.assignOwner(bakery, userId(merchant));

    String first = orderPickup(customer, bakery, listingId(BAKERY, "Franzbrötchen"), 4);
    advance(merchant, first, "ACCEPTED", 200, null);
    mvc.perform(auth(post("/api/v1/orders/" + first + "/cancel"), customer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));

    String second = orderPickup(customer, bakery, listingId(BAKERY, "Franzbrötchen"), 4);
    MvcResult order = mvc.perform(auth(get("/api/v1/orders/" + second), customer)).andReturn();
    OffsetDateTime cutoff = OffsetDateTime.parse(read(order, "$.slot.orderCutoffAt"));
    String slotId = read(order, "$.slot.id");
    int remainingBefore = read(order, "$.slot.remainingCapacity");

    MvcResult rejected =
        mvc.perform(json(auth(post("/api/v1/merchant/orders/" + second + "/status"), merchant), "{\"status\":\"REJECTED\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REJECTED"))
            .andReturn();
    assertThat((Integer) read(rejected, "$.slot.remainingCapacity")).isEqualTo(remainingBefore + 1);
    assertThat((String) read(rejected, "$.slot.id")).isEqualTo(slotId);

    String third = orderPickup(customer, bakery, listingId(BAKERY, "Franzbrötchen"), 4);
    MvcResult thirdOrder = mvc.perform(auth(get("/api/v1/orders/" + third), customer)).andReturn();
    OffsetDateTime thirdCutoff = OffsetDateTime.parse(read(thirdOrder, "$.slot.orderCutoffAt"));
    clock.set(thirdCutoff.toInstant().plus(Duration.ofMinutes(1)));
    mvc.perform(auth(post("/api/v1/orders/" + third + "/cancel"), customer))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("ORDER_NOT_CANCELLABLE"));
    assertThat(cutoff).isNotNull();
  }

  @Test
  void placingTwiceWithTheSameIdempotencyKeyReturnsTheSameOrder() throws Exception {
    String customer = registerUser();
    UUID bakery = marketId(BAKERY);
    setQuantity(customer, bakery, listingId(BAKERY, "Franzbrötchen"), 4);
    reserve(customer, bakery, "PICKUP", availableSlot(slots(null, bakery, "PICKUP", null), 1), null);
    String key = "key-" + UUID.randomUUID();
    MvcResult first =
        mvc.perform(auth(post("/api/v1/markets/" + bakery + "/orders"), customer).header("Idempotency-Key", key))
            .andExpect(status().isCreated())
            .andReturn();
    MvcResult second =
        mvc.perform(auth(post("/api/v1/markets/" + bakery + "/orders"), customer).header("Idempotency-Key", key))
            .andExpect(status().isCreated())
            .andReturn();
    assertThat((String) read(second, "$.id")).isEqualTo(read(first, "$.id"));
    assertThat(objects(mvc.perform(auth(get("/api/v1/orders"), customer)).andReturn(), "$")).hasSize(1);
  }

  private void advance(String merchant, String orderId, String status, int expectedStatus, String expectedCode) throws Exception {
    MvcResult result =
        mvc.perform(json(auth(post("/api/v1/merchant/orders/" + orderId + "/status"), merchant), "{\"status\":\"" + status + "\"}"))
            .andReturn();
    assertThat(result.getResponse().getStatus()).as(status).isEqualTo(expectedStatus);
    if (expectedCode != null) {
      assertThat((String) read(result, "$.code")).isEqualTo(expectedCode);
    } else {
      assertThat((String) read(result, "$.status")).isEqualTo(status);
    }
  }

  private int remaining(String token, UUID marketId, String addressId, String slotId) throws Exception {
    return slots(token, marketId, "DELIVERY", addressId).stream()
        .filter(s -> slotId.equals(s.get("id")))
        .map(s -> ((Number) s.get("remainingCapacity")).intValue())
        .findFirst()
        .orElseThrow();
  }
}
