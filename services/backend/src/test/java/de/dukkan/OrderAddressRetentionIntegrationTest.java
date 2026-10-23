package de.dukkan;

import static org.assertj.core.api.Assertions.assertThat;

import de.dukkan.order.application.OrderAddressRetentionJob;
import de.dukkan.support.IntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/** Delivery addresses leave finished orders after the configured period; the order itself stays. */
@TestPropertySource(properties = "dukkan.privacy.order-address-retention=P3Y")
class OrderAddressRetentionIntegrationTest extends IntegrationTest {

  @Autowired OrderAddressRetentionJob job;

  private void deliveredTo(String orderId, String status) {
    jdbc.sql(
            "UPDATE customer_order SET status = ?, address_street = 'Lange Reihe', address_house_number = '12',"
                + " address_postal_code = '20099', address_city = 'Hamburg', address_delivery_instructions = 'Hinterhof',"
                + " address_lat = 53.55, address_lng = 10.01 WHERE id = ?")
        .params(status, UUID.fromString(orderId))
        .update();
  }

  private Map<String, Object> address(String orderId) {
    return jdbc.sql(
            "SELECT customer_name, address_street, address_house_number, address_postal_code, address_delivery_instructions,"
                + " address_lat FROM customer_order WHERE id = ?")
        .param(UUID.fromString(orderId))
        .query()
        .singleRow();
  }

  @Test
  void removesOldAddressesOfFinishedOrdersOnly() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Aufbewahrungsmarkt");
    UUID listing = addListing(merchant, market, "Ayran", 300);
    String customer = registerUser();
    String old = orderPickup(customer, market, listing, 5);
    String stillOpen = orderPickup(customer, market, listing, 5);
    String recent = orderPickup(customer, market, listing, 5);
    deliveredTo(old, "COMPLETED");
    deliveredTo(stillOpen, "ACCEPTED");
    deliveredTo(recent, "COMPLETED");
    // Placed four years ago (the access token would not survive moving the clock that far).
    jdbc.sql("UPDATE customer_order SET placed_at = placed_at - interval '4 years' WHERE id IN (?, ?)")
        .params(UUID.fromString(old), UUID.fromString(stillOpen))
        .update();

    assertThat(job.minimise()).isOne();

    assertThat(address(old))
        .containsEntry("address_street", "-")
        .containsEntry("address_house_number", null)
        .containsEntry("address_delivery_instructions", null)
        .containsEntry("address_lat", null)
        .containsEntry("address_postal_code", "20099")
        .containsEntry("customer_name", "Test User");
    assertThat(address(stillOpen)).containsEntry("address_house_number", "12");
    assertThat(address(recent)).containsEntry("address_house_number", "12");
    assertThat(job.minimise()).isZero();
  }
}
