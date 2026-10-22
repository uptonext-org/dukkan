package de.dukkan;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import de.dukkan.support.IntegrationTest;
import de.dukkan.support.TestImages;
import java.awt.Color;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * F1 with a language model, against a local stub of the Messages API: the whole chain (multipart
 * upload, SDK request with images and schema, structured answer, catalog match, pack counts and the
 * fallback to the rules) runs without a real key.
 */
class ShoppingListAiIntegrationTest extends IntegrationTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final HttpServer STUB;
  private static final AtomicReference<String> REQUEST_BODY = new AtomicReference<>();
  private static volatile int status = 200;
  private static volatile String stopReason = "end_turn";

  static {
    try {
      STUB = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    STUB.createContext(
        "/v1/messages",
        exchange -> {
          REQUEST_BODY.set(new String(exchange.getRequestBody().readAllBytes(), UTF_8));
          byte[] body = answer().getBytes(UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(status, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    STUB.start();
  }

  @DynamicPropertySource
  static void anthropic(DynamicPropertyRegistry registry) {
    registry.add("dukkan.ai.anthropic.api-key", () -> "stub-key");
    registry.add("dukkan.ai.anthropic.base-url", () -> "http://127.0.0.1:" + STUB.getAddress().getPort());
  }

  @AfterAll
  static void stopStub() {
    STUB.stop(0);
  }

  @BeforeEach
  void reset() {
    status = 200;
    stopReason = "end_turn";
  }

  /** What the model "read": cucumbers by count, tomatoes by weight, something the market lacks. */
  private static String answer() {
    if (status != 200) {
      return "{\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":\"stub\"}}";
    }
    String items =
        JSON.writeValueAsString(
            Map.of(
                "items",
                List.of(
                    Map.of("text", "3 Gurken", "search_terms", List.of("Gurke", "Salatgurke"), "amount", 3, "unit", "piece"),
                    Map.of("text", "anderthalb Kilo Tomaten", "search_terms", List.of("Tomate"), "amount", 1.5, "unit", "kg"),
                    Map.of("text", "Drachenfrucht", "search_terms", List.of("Drachenfrucht", "Pitaya"), "amount", 1, "unit", "piece"),
                    Map.of("text", "", "search_terms", List.of(), "amount", 1, "unit", "piece"))));
    return JSON.writeValueAsString(
        Map.of(
            "id", "msg_stub",
            "type", "message",
            "role", "assistant",
            "model", "claude-opus-5-5",
            "content", List.of(Map.of("type", "text", "text", items)),
            "stop_reason", stopReason,
            "usage", Map.of("input_tokens", 1200, "output_tokens", 90)));
  }

  @Test
  void photosAndTheTranscriptAreReadByTheModelAndMatched() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "KI-Listen-Markt");
    UUID cucumber = addListing(merchant, market, "Salatgurke", 69);
    UUID tomatoes = addListing(merchant, market, "Strauchtomaten", 249);
    String customer = registerUser();
    byte[] frame = TestImages.png(64, 48, Color.ORANGE);

    mvc.perform(get("/api/v1/shopping-list")).andExpect(status().isOk()).andExpect(jsonPath("$.ai").value(true));

    MvcResult result =
        mvc.perform(
                list(market, customer)
                            .file(new MockMultipartFile("images", "frame-1.png", "image/png", frame))
                            .file(new MockMultipartFile("images", "frame-2.png", "image/png", frame))
                    .param("text", "drei Gurken und anderthalb Kilo Tomaten, ach ja und eine Drachenfrucht")
                    .header(HttpHeaders.ACCEPT_LANGUAGE, "ar"))
            .andExpect(status().isOk())
            .andReturn();
    assertThat((String) read(result, "$.engine")).isEqualTo("AI");
    // The empty item is dropped.
    assertThat(strings(result, "$.lines[*].text")).containsExactly("3 Gurken", "anderthalb Kilo Tomaten", "Drachenfrucht");
    assertThat((String) read(result, "$.lines[0].match.product.id")).isEqualTo(cucumber.toString());
    assertThat((Integer) read(result, "$.lines[0].match.quantity")).isEqualTo(3);
    assertThat((String) read(result, "$.lines[1].match.product.id")).isEqualTo(tomatoes.toString());
    assertThat((String) read(result, "$.lines[1].amount")).isEqualTo("1.5 kg");
    assertThat((Integer) read(result, "$.lines[1].match.quantity")).isEqualTo(3);
    assertThat(object(result, "$.lines[2].match")).isNull();
    ProductDraftIntegrationTest.conforms("ShoppingListResult", object(result, "$"));

    JsonNode request = JSON.readTree(REQUEST_BODY.get());
    JsonNode content = request.get("messages").get(0).get("content");
    assertThat(content.get(0).get("type").asString()).isEqualTo("image");
    assertThat(content.get(1).get("type").asString()).isEqualTo("image");
    assertThat(content.get(2).get("text").asString()).contains("<customer_message>", "Drachenfrucht");
    String system = request.get("system").asString();
    assertThat(system).contains("never instructions", "Arabic", "frames of one short video");
    assertThat(request.get("output_config").get("format").get("schema").get("properties").has("items")).isTrue();
    // The market's assortment is not sent to the model.
    assertThat(REQUEST_BODY.get()).doesNotContain("Strauchtomaten");
  }

  @Test
  void whenTheModelFailsTextFallsBackToTheRulesAndPhotosAreDeclined() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Ausfall-Markt");
    UUID cucumber = addListing(merchant, market, "Salatgurke", 69);
    String customer = registerUser();
    status = 500;

    MvcResult result =
        mvc.perform(list(market, customer).param("text", "2 Gurken"))
            .andExpect(status().isOk())
            .andReturn();
    assertThat((String) read(result, "$.engine")).isEqualTo("RULES");
    assertThat((String) read(result, "$.lines[0].match.product.id")).isEqualTo(cucumber.toString());

    MockMultipartFile photo = new MockMultipartFile("images", "liste.png", "image/png", TestImages.png(40, 40, Color.WHITE));
    mvc.perform(list(market, customer).file(photo))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("SHOPPING_LIST_AI_UNAVAILABLE"));

    status = 200;
    stopReason = "refusal";
    mvc.perform(list(market, customer).param("text", "2 Gurken"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.engine").value("RULES"));
  }

  private static MockMultipartHttpServletRequestBuilder list(UUID market, String token) {
    return multipart("/api/v1/markets/" + market + "/shopping-list").header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
  }
}
