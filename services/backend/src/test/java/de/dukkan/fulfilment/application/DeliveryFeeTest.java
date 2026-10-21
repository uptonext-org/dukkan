package de.dukkan.fulfilment.application;

import static org.assertj.core.api.Assertions.assertThat;

import de.dukkan.shared.domain.FulfilmentType;
import org.junit.jupiter.api.Test;

class DeliveryFeeTest {

  @Test
  void deliveryCostsTheMarketFeeBelowTheThreshold() {
    assertThat(DeliveryFee.feeCents(FulfilmentType.DELIVERY, 290, 4000L, 3999)).isEqualTo(290);
  }

  @Test
  void deliveryIsFreeAtOrAboveTheThreshold() {
    assertThat(DeliveryFee.feeCents(FulfilmentType.DELIVERY, 290, 4000L, 4000)).isZero();
    assertThat(DeliveryFee.feeCents(FulfilmentType.DELIVERY, 290, 4000L, 12_000)).isZero();
  }

  @Test
  void withoutThresholdTheFeeAlwaysApplies() {
    assertThat(DeliveryFee.feeCents(FulfilmentType.DELIVERY, 490, null, 100_000)).isEqualTo(490);
  }

  @Test
  void pickupAndNoReservationAreAlwaysFree() {
    assertThat(DeliveryFee.feeCents(FulfilmentType.PICKUP, 290, 4000L, 100)).isZero();
    assertThat(DeliveryFee.feeCents(null, 290, 4000L, 100)).isZero();
  }
}
