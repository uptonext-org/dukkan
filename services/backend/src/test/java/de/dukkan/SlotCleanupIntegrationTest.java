package de.dukkan;

import static org.assertj.core.api.Assertions.assertThat;

import de.dukkan.fulfilment.application.SlotCleanupJob;
import de.dukkan.support.IntegrationTest;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Expired, unused slots are deleted; slots used by orders survive. */
class SlotCleanupIntegrationTest extends IntegrationTest {

  @Autowired private SlotCleanupJob cleanup;

  @Test
  void deletesOnlyExpiredUnusedSlots() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Aufräum Testmarkt");
    UUID listing = addListing(merchant, market, "Äpfel Elstar", 2000);
    String customer = registerUser();
    String orderId = orderPickup(customer, market, listing, 1);
    UUID orderedSlot = jdbc.sql("SELECT slot_id FROM customer_order WHERE id = :id").param("id", UUID.fromString(orderId)).query(UUID.class).single();
    long before = slotCount(market);
    assertThat(before).isGreaterThan(1);

    // Nothing has ended yet.
    cleanup.deleteExpiredSlots();
    assertThat(slotCount(market)).isEqualTo(before);

    // Ten days later every generated slot has ended; only the ordered one is kept.
    clock.advance(Duration.ofDays(10));
    assertThat(cleanup.deleteExpiredSlots()).isGreaterThanOrEqualTo((int) before - 1);
    assertThat(jdbc.sql("SELECT id FROM fulfilment_slot WHERE market_id = :m").param("m", market).query(UUID.class).list())
        .containsExactly(orderedSlot);
  }

  private long slotCount(UUID market) {
    return jdbc.sql("SELECT count(*) FROM fulfilment_slot WHERE market_id = :m").param("m", market).query(Long.class).single();
  }
}
