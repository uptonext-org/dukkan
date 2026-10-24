package de.dukkan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.shared.events.OutboxPublisher;
import de.dukkan.support.CapturingMailer;
import de.dukkan.support.CapturingMailer.Sent;
import de.dukkan.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/** The customer gets the order by e-mail: confirmation, changes after missing items, a declined order. */
@TestPropertySource(
    properties = {
      "dukkan.platform.payment=ON_SITE",
      "dukkan.platform.web-url=https://dukkan.example/",
      "dukkan.platform.terms-url=https://dukkan.example/agb",
      "dukkan.platform.privacy-url=https://dukkan.example/datenschutz",
      "dukkan.platform.support-email=hilfe@dukkan.example"
    })
class OrderMailIntegrationTest extends IntegrationTest {

  @Autowired CapturingMailer mailer;
  @Autowired OutboxPublisher publisher;

  private List<Sent> awaitMails(String email, int count) throws InterruptedException {
    for (int i = 0; i < 50; i++) {
      publisher.publishPending();
      List<Sent> mails = mailer.to(email);
      if (mails.size() >= count) {
        return mails;
      }
      Thread.sleep(100);
    }
    throw new AssertionError("Expected " + count + " mail(s) to " + email);
  }

  @Test
  void confirmsTheOrderThenSendsTheChangedOrder() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Mail-Markt");
    UUID ayran = addListing(merchant, market, "Ayran", 300);
    String email = uniqueEmail();
    String customer = register(email, "CUSTOMER");
    String order = orderPickup(customer, market, ayran, 5);

    Sent placed = awaitMails(email, 1).getFirst();
    assertThat(placed.subject()).startsWith("Deine Bestellung DK-").endsWith(" bei Mail-Markt");
    assertThat(placed.body())
        .contains("Hallo Test,")
        .contains("bestätigt, dass sie eingegangen ist")
        .contains("5 × Ayran")
        .contains("Gesamt (inkl. MwSt.): 15,00")
        .contains("Abholung: ")
        .contains("Bezahlung: direkt beim Markt")
        .contains("Verkäufer:")
        .contains("Bestellung ansehen: https://dukkan.example/orders/" + order)
        .contains("AGB: https://dukkan.example/agb")
        .contains("Hilfe: hilfe@dukkan.example");

    mvc.perform(json(auth(post("/api/v1/merchant/orders/" + order + "/status"), merchant), toJson(Map.of("status", "ACCEPTED"))))
        .andExpect(status().isOk());
    mvc.perform(
            json(
                auth(put("/api/v1/merchant/orders/" + order + "/items"), merchant),
                toJson(Map.of("items", List.of(Map.of("lineNo", 1, "fulfilledQuantity", 3))))))
        .andExpect(status().isOk());

    // Accepting sends no mail (the app says so); the changed order does.
    Sent adjusted = awaitMails(email, 2).get(1);
    assertThat(adjusted.subject()).endsWith("wurde angepasst");
    assertThat(adjusted.body()).contains("3 (statt 5) × Ayran").contains("Gesamt (inkl. MwSt.): 9,00").contains("Ursprünglich: 15,00");
  }

  @Test
  void tellsTheCustomerInTheirLanguageWhenAMarketDeclines() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Absage-Markt");
    UUID tea = addListing(merchant, market, "Schwarzer Tee Rize", 400);
    String email = uniqueEmail();
    String customer = register(email, "CUSTOMER");
    mvc.perform(json(auth(patch("/api/v1/me"), customer), "{\"preferredLocale\":\"en\"}")).andExpect(status().isOk());
    String order = orderPickup(customer, market, tea, 4);

    mvc.perform(json(auth(post("/api/v1/merchant/orders/" + order + "/status"), merchant), toJson(Map.of("status", "REJECTED"))))
        .andExpect(status().isOk());

    List<Sent> mails = awaitMails(email, 2);
    assertThat(mails.getFirst().subject()).startsWith("Your order DK-");
    assertThat(mails.get(1).subject()).startsWith("Absage-Markt cannot accept your order");
    assertThat(mails.get(1).body()).contains("You will not be charged").doesNotContain("Payment:");
  }
}
