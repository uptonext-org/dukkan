package de.dukkan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.support.IntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Business metrics for operations; the metrics endpoint is not public on the main port. */
class MetricsIntegrationTest extends IntegrationTest {

  @Autowired private MeterRegistry meters;
  @Autowired private de.dukkan.shared.ai.AiUsageRecorder aiUsage;

  private double count(String name, String... tags) {
    var counter = meters.find(name).tags(tags).counter();
    return counter == null ? 0 : counter.count();
  }

  @Test
  void ordersAndStatusChangesAreCountedAndTheOutboxIsWatched() throws Exception {
    double placed = count("dukkan.orders.placed", "fulfilment", "PICKUP");
    double accepted = count("dukkan.orders.status_changes", "status", "ACCEPTED", "by", "merchant");
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Metrikmarkt");
    UUID listing = addListing(merchant, market, "Ayran", 650);
    String order = orderPickup(registerUser(), market, listing, 1);
    mvc.perform(json(auth(post("/api/v1/merchant/orders/" + order + "/status"), merchant), toJson(Map.of("status", "ACCEPTED"))))
        .andExpect(status().isOk());

    assertThat(count("dukkan.orders.placed", "fulfilment", "PICKUP")).isEqualTo(placed + 1);
    assertThat(count("dukkan.orders.status_changes", "status", "ACCEPTED", "by", "merchant")).isEqualTo(accepted + 1);
    var pending = meters.find("dukkan.outbox.pending").gauge();
    assertThat(pending).isNotNull();
    assertThat(pending.value()).isGreaterThanOrEqualTo(0);
    assertThat(meters.find("dukkan.outbox.oldest.pending.seconds").gauge()).isNotNull();
  }

  @Test
  void languageModelCallsHaveLatencyBucketsForTheDashboard() {
    aiUsage.record("translation", "ok", 120, 40, java.time.Duration.ofMillis(900));
    var timer = meters.find("dukkan.ai.requests").tags("feature", "translation", "outcome", "ok").timer();
    assertThat(timer).isNotNull();
    assertThat(timer.takeSnapshot().histogramCounts()).isNotEmpty();
    assertThat(meters.find("dukkan.ai.tokens").tags("feature", "translation", "direction", "input").counter().count()).isGreaterThanOrEqualTo(120);
  }

  @Test
  void prometheusIsNotPublicOnTheMainPort() throws Exception {
    mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
    mvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }
}
