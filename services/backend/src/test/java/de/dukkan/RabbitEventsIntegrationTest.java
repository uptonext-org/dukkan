package de.dukkan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.support.IntegrationTest;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * B-37 with the RabbitMQ transport: the outbox publisher sends order events to RabbitMQ 4.3.6
 * (publisher confirms), the listener consumes them and the notification arrives in the inbox.
 */
class RabbitEventsIntegrationTest extends IntegrationTest {

  @SuppressWarnings("resource")
  private static final GenericContainer<?> RABBIT =
      new GenericContainer<>("rabbitmq:4.3.6-management-alpine")
          .withEnv("RABBITMQ_DEFAULT_USER", "dukkan")
          .withEnv("RABBITMQ_DEFAULT_PASS", "dukkan-test")
          .withExposedPorts(5672)
          .waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1).withStartupTimeout(Duration.ofMinutes(2)));

  static {
    RABBIT.start();
  }

  @DynamicPropertySource
  static void rabbit(DynamicPropertyRegistry registry) {
    registry.add("dukkan.events.transport", () -> "rabbitmq");
    registry.add("dukkan.events.rabbitmq-uri", () -> "amqp://dukkan:dukkan-test@" + RABBIT.getHost() + ":" + RABBIT.getMappedPort(5672));
    registry.add("dukkan.events.publish-interval", () -> "PT0.2S");
  }

  @AfterAll
  static void stop() {
    RABBIT.stop();
  }

  @Autowired private RabbitAdmin admin;

  @Test
  void orderEventsTravelThroughRabbitMqIntoTheInbox() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Rabbit-Markt");
    UUID listing = addListing(merchant, market, "Ayran", 600);
    String order = orderPickup(registerUser(), market, listing, 1);

    MvcResult inbox = null;
    for (int i = 0; i < 100; i++) {
      inbox = mvc.perform(auth(get("/api/v1/me/notifications"), merchant)).andExpect(status().isOk()).andReturn();
      if ((Integer) read(inbox, "$.totalItems") > 0) {
        break;
      }
      Thread.sleep(100);
    }
    assertThat((Integer) read(inbox, "$.totalItems")).isEqualTo(1);
    assertThat((String) read(inbox, "$.items[0].orderId")).isEqualTo(order);
    assertThat(jdbc.sql("SELECT count(*) FROM outbox_event WHERE aggregate_id = :id AND published_at IS NOT NULL").param("id", UUID.fromString(order)).query(Long.class).single())
        .isEqualTo(1);

    QueueInformation queue = admin.getQueueInfo("dukkan.backend.events");
    assertThat(queue).isNotNull();
    QueueInformation deadLetters = admin.getQueueInfo("dukkan.backend.events.dlq");
    assertThat(deadLetters).isNotNull();
    assertThat(deadLetters.getMessageCount()).isZero();
    mvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }
}
