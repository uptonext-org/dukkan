package de.dukkan;

import static org.assertj.core.api.Assertions.assertThat;

import de.dukkan.address.application.AddressService;
import de.dukkan.address.domain.AddressDetails;
import de.dukkan.cart.application.CartService;
import de.dukkan.delivery.application.DeliveryService;
import de.dukkan.delivery.application.SlotView;
import de.dukkan.identity.application.IdentityService;
import de.dukkan.identity.application.RegisterCommand;
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

/** Concurrent reservations must never oversell a slot (pessimistic row lock on delivery_slot). */
class SlotCapacityConcurrencyIntegrationTest extends IntegrationTest {

  private static final int USERS = 12;

  @Autowired private IdentityService identity;
  @Autowired private AddressService addresses;
  @Autowired private DeliveryService delivery;
  @Autowired private CartService carts;

  private record Customer(UUID userId, UUID addressId) {}

  @Test
  void concurrentReservationsDoNotExceedCapacity() throws Exception {
    List<Customer> customers = new ArrayList<>();
    for (int i = 0; i < USERS; i++) {
      UUID userId =
          identity.register(new RegisterCommand(uniqueEmail(), "correct-horse-battery", "C", "U")).user().id();
      UUID addressId =
          addresses
              .create(userId, new AddressDetails(null, "Domplatz", String.valueOf(i + 1), null, "50667", "Köln", null), false)
              .id();
      customers.add(new Customer(userId, addressId));
    }
    List<SlotView> slots = delivery.listSlotsForAddress(customers.getFirst().userId(), customers.getFirst().addressId());
    SlotView slot = slots.get(slots.size() / 2);
    assertThat(slot.remainingCapacity()).isEqualTo(8);

    CountDownLatch start = new CountDownLatch(1);
    List<Future<String>> outcomes = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(USERS)) {
      for (Customer customer : customers) {
        Callable<String> task =
            () -> {
              start.await();
              try {
                carts.reserveSlot(customer.userId(), slot.id(), customer.addressId());
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

      assertThat(results).filteredOn("RESERVED"::equals).hasSize(8);
      assertThat(results).filteredOn(ErrorCodes.SLOT_FULL::equals).hasSize(USERS - 8);
    }
    SlotView after = delivery.getSlot(slot.id());
    assertThat(after.remainingCapacity()).isZero();
    assertThat(after.available()).isFalse();
  }
}
