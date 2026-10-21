package de.dukkan.order.domain;

import static de.dukkan.order.domain.OrderStatus.ACCEPTED;
import static de.dukkan.order.domain.OrderStatus.CANCELLED;
import static de.dukkan.order.domain.OrderStatus.COMPLETED;
import static de.dukkan.order.domain.OrderStatus.OUT_FOR_DELIVERY;
import static de.dukkan.order.domain.OrderStatus.PLACED;
import static de.dukkan.order.domain.OrderStatus.READY_FOR_PICKUP;
import static de.dukkan.order.domain.OrderStatus.REJECTED;
import static de.dukkan.shared.domain.FulfilmentType.DELIVERY;
import static de.dukkan.shared.domain.FulfilmentType.PICKUP;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OrderStatusTest {

  @Test
  void merchantTransitionsFollowTheContract() {
    assertThat(PLACED.canAdvanceTo(ACCEPTED, PICKUP)).isTrue();
    assertThat(PLACED.canAdvanceTo(REJECTED, DELIVERY)).isTrue();
    assertThat(PLACED.canAdvanceTo(COMPLETED, PICKUP)).isFalse();
    assertThat(ACCEPTED.canAdvanceTo(READY_FOR_PICKUP, PICKUP)).isTrue();
    assertThat(ACCEPTED.canAdvanceTo(OUT_FOR_DELIVERY, PICKUP)).isFalse();
    assertThat(ACCEPTED.canAdvanceTo(OUT_FOR_DELIVERY, DELIVERY)).isTrue();
    assertThat(ACCEPTED.canAdvanceTo(READY_FOR_PICKUP, DELIVERY)).isFalse();
    assertThat(READY_FOR_PICKUP.canAdvanceTo(COMPLETED, PICKUP)).isTrue();
    assertThat(OUT_FOR_DELIVERY.canAdvanceTo(COMPLETED, DELIVERY)).isTrue();
    for (OrderStatus terminal : new OrderStatus[] {REJECTED, COMPLETED, CANCELLED}) {
      for (OrderStatus target : OrderStatus.values()) {
        assertThat(terminal.canAdvanceTo(target, PICKUP)).isFalse();
        assertThat(terminal.canAdvanceTo(target, DELIVERY)).isFalse();
      }
    }
    assertThat(PLACED.canAdvanceTo(CANCELLED, PICKUP)).as("cancel is a customer action").isFalse();
  }

  @Test
  void onlyRejectedAndCancelledReleaseCapacity() {
    assertThat(REJECTED.releasesCapacity()).isTrue();
    assertThat(CANCELLED.releasesCapacity()).isTrue();
    assertThat(COMPLETED.releasesCapacity()).isFalse();
    assertThat(PLACED.isCancellableByCustomer()).isTrue();
    assertThat(ACCEPTED.isCancellableByCustomer()).isTrue();
    assertThat(READY_FOR_PICKUP.isCancellableByCustomer()).isFalse();
  }
}
