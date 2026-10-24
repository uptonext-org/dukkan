package de.dukkan.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import de.dukkan.address.application.AddressView;
import de.dukkan.fulfilment.application.SlotView;
import de.dukkan.legal.application.PlatformSettings;
import de.dukkan.market.application.MarketDetail;
import de.dukkan.market.application.MarketSummary;
import de.dukkan.order.application.OrderView;
import de.dukkan.shared.domain.FulfilmentType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OrderMailsTest {

  private static final PlatformSettings SIMULATED = new PlatformSettings(null, null, null, null, null, PlatformSettings.PaymentMode.SIMULATED, null);

  private static OrderView delivery() {
    Instant start = Instant.parse("2026-11-07T09:00:00Z");
    SlotView slot = Mockito.mock(SlotView.class);
    Mockito.when(slot.startsAt()).thenReturn(start);
    Mockito.when(slot.endsAt()).thenReturn(start.plusSeconds(3600));
    AddressView address = Mockito.mock(AddressView.class);
    Mockito.when(address.street()).thenReturn("Lange Reihe");
    Mockito.when(address.houseNumber()).thenReturn("12");
    Mockito.when(address.postalCode()).thenReturn("20099");
    Mockito.when(address.city()).thenReturn("Hamburg");
    return new OrderView(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "DK-20261106-000042",
        "PLACED",
        Instant.parse("2026-11-06T17:30:00Z"),
        FulfilmentType.DELIVERY,
        List.of(new OrderView.Item(1, UUID.randomUUID(), "Ayran", 100, 3, 300, null)),
        300,
        0,
        290,
        590,
        slot,
        address,
        null,
        null);
  }

  private static MarketDetail market() {
    MarketSummary summary = Mockito.mock(MarketSummary.class);
    Mockito.when(summary.name()).thenReturn("Anatolia");
    return new MarketDetail(summary, null, null, List.of(), "de", new MarketDetail.Imprint("Anatolia GmbH", "Steindamm 1, 20099 Hamburg", null));
  }

  @Test
  void writesArabicWithTheSameAmountsAndBerlinTimes() {
    OrderMails.Mail mail = OrderMails.build(new OrderMails.Input(OrderMails.Kind.PLACED, "ar", "ليلى", delivery(), market(), SIMULATED, null));
    assertThat(mail.subject()).isEqualTo("طلبك DK-20261106-000042 من Anatolia");
    assertThat(mail.body())
        .startsWith("مرحباً ليلى،")
        .contains("التوصيل: ")
        .contains("10:00–11:00")
        .contains("Lange Reihe 12, 20099 Hamburg")
        .contains("رسوم التوصيل: 2,90")
        .contains("تشغيل تجريبي")
        .contains("Anatolia GmbH")
        .doesNotContain("عرض الطلب");
  }

  @Test
  void usesEnglishAmountsInEnglish() {
    OrderMails.Mail mail = OrderMails.build(new OrderMails.Input(OrderMails.Kind.CANCELLED, "en", "Ada", delivery(), market(), SIMULATED, null));
    assertThat(mail.subject()).isEqualTo("Your order DK-20261106-000042 is cancelled");
    assertThat(mail.body()).contains("Total (incl. VAT): €5.90").doesNotContain("Test operation");
  }
}
