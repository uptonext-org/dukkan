package de.dukkan;

import static org.assertj.core.api.Assertions.assertThat;

import de.dukkan.cart.application.CartService;
import de.dukkan.fulfilment.application.FulfilmentService;
import de.dukkan.fulfilment.application.SlotView;
import de.dukkan.i18n.application.ContentLocale;
import de.dukkan.shared.domain.FulfilmentType;
import de.dukkan.shared.error.ConflictException;
import de.dukkan.shared.error.ErrorCodes;
import de.dukkan.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Concurrent reservations must never oversell a slot (pessimistic row lock on fulfilment_slot). */
class SlotCapacityConcurrencyIntegrationTest extends IntegrationTest {

  private static final int USERS = 16;
  private static final int PICKUP_CAPACITY = 10;

  @Autowired private FulfilmentService fulfilment;
  @Autowired private CartService carts;

  @Test
  void concurrentReservationsDoNotExceedCapacity() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Kapazität Testmarkt");
    UUID listing = addListing(merchant, market, "Äpfel Elstar", 299);
    List<UUID> customers = new ArrayList<>();
    for (int i = 0; i < USERS; i++) {
      String token = registerUser();
      setQuantity(token, market, listing, 1); // empty carts cannot reserve
      customers.add(userId(token));
    }
    List<SlotView> slots = fulfilment.listSlots(market, FulfilmentType.PICKUP, null, null);
    SlotView slot = slots.stream().filter(SlotView::available).skip(3).findFirst().orElseThrow();
    assertThat(slot.remainingCapacity()).isEqualTo(PICKUP_CAPACITY);

    CountDownLatch start = new CountDownLatch(1);
    List<Future<String>> outcomes = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(USERS)) {
      for (UUID customer : customers) {
        Callable<String> task =
            () -> {
              start.await();
              try {
                carts.reserve(customer, market, FulfilmentType.PICKUP, slot.id(), null, ContentLocale.of(null));
                return "RESERVED";
              } catch (ConflictException e) {
                return e.code();
              }
            };
        outcomes.add(pool.submit(task));
      }
      start.countDown();
      List<String> results = new ArrayList<>();
      for (Future<String> outcome : outcomes) {
        results.add(outcome.get());
      }

      assertThat(results).filteredOn("RESERVED"::equals).hasSize(PICKUP_CAPACITY);
      assertThat(results).filteredOn(ErrorCodes.SLOT_FULL::equals).hasSize(USERS - PICKUP_CAPACITY);
    }
    SlotView after = fulfilment.getSlot(slot.id());
    assertThat(after.remainingCapacity()).isZero();
    assertThat(after.available()).isFalse();
  }
}
