package de.dukkan.capture.infrastructure;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.sun.net.httpserver.HttpServer;
import de.dukkan.capture.application.ProductDraftExtractor.CategoryOption;
import de.dukkan.capture.application.ProductDraftExtractor.Image;
import de.dukkan.capture.application.ProductDraftExtractor.Outcome;
import de.dukkan.capture.application.ProductDraftExtractor.Request;
import de.dukkan.capture.domain.Allergens;
import de.dukkan.capture.domain.ExtractedProduct;
import de.dukkan.shared.ai.ModelImages;
import de.dukkan.shared.config.DukkanProperties;
import de.dukkan.support.TestImages;
import java.awt.Color;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Runs the real SDK against a local stub of {@code POST /v1/messages}; no API key or network needed. */
class ClaudeProductDraftExtractorTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private static final Request REQUEST =
      new Request(
          "Sucuk Gazi 400g 4,49",
          null,
          List.of("de", "en", "ar"),
          List.of(
              new CategoryOption("meat", Map.of("de", "Fleisch & Geflügel", "en", "Meat & Poultry")),
              new CategoryOption("dairy", Map.of("de", "Milch, Käse & Eier", "en", "Dairy, Cheese & Eggs"))),
          List.of("TURKISH", "ARABIC"),
          Allergens.CODES);

  private static final String MODEL_ANSWER =
      """
      {"names":{"de":"Sucuk","en":"Sucuk beef sausage","ar":"سجق"},
       "descriptions":{"de":"Türkische Knoblauchwurst aus Rind.","en":"","ar":""},
       "brand":"Gazi","unitSize":"400 g","netQuantity":0.4,"netUnit":"kg","categorySlug":"meat",
       "cuisineCodes":["TURKISH"],"organic":false,"allergens":[],"keywords":["sucuk","sujuk"],
       "priceCents":449,"barcode":null}
      """;

  private HttpServer server;
  private final AtomicReference<String> requestBody = new AtomicReference<>();
  private final AtomicReference<String> betaHeader = new AtomicReference<>();
  private final AtomicReference<String> apiKeyHeader = new AtomicReference<>();
  private final AtomicInteger calls = new AtomicInteger();
  private volatile int status = 200;
  private volatile String responseBody = message("end_turn", MODEL_ANSWER, null);
  private final List<AnthropicClient> clients = new ArrayList<>();

  @BeforeEach
  void startStub() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/v1/messages",
        exchange -> {
          calls.incrementAndGet();
          requestBody.set(new String(exchange.getRequestBody().readAllBytes(), UTF_8));
          betaHeader.set(exchange.getRequestHeaders().getFirst("anthropic-beta"));
          // SDK 3 (API version 2026-10-21) sends the API key as a bearer token.
          apiKeyHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
          byte[] body = responseBody.getBytes(UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(status, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
  }

  @AfterEach
  void stopStub() {
    clients.forEach(AnthropicClient::close);
    server.stop(0);
  }

  private static String message(String stopReason, @Nullable String text, @Nullable String stopDetails) {
    String content = text == null ? "[]" : "[{\"type\":\"text\",\"text\":" + JSON.writeValueAsString(text) + "}]";
    return """
        {"id":"msg_test","type":"message","role":"assistant","model":"claude-opus-5-5","content":%s,
         "stop_reason":"%s","stop_sequence":null,%s"usage":{"input_tokens":120,"output_tokens":80}}
        """
        .formatted(content, stopReason, stopDetails == null ? "" : "\"stop_details\":" + stopDetails + ",");
  }

  private static DukkanProperties.Anthropic settings(boolean fallbacks) {
    return new DukkanProperties.Anthropic("test-key", "claude-opus-5-5", "low", Duration.ofSeconds(10), 8000, fallbacks, null);
  }

  private ClaudeProductDraftExtractor extractor(boolean fallbacks) {
    AnthropicClient client =
        AnthropicOkHttpClient.builder()
            .apiKey("test-key")
            .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
            .maxRetries(0)
            .timeout(Duration.ofSeconds(10))
            .build();
    clients.add(client);
    return new ClaudeProductDraftExtractor(settings(fallbacks), JSON, client);
  }

  @Test
  void sendsSchemaEffortFallbacksAndTheMerchantTextAsData() {
    Outcome outcome = extractor(true).extract(REQUEST);

    assertThat(outcome).isInstanceOf(Outcome.Extracted.class);
    assertThat(apiKeyHeader.get()).isEqualTo("Bearer test-key");
    assertThat(betaHeader.get()).contains(ClaudeProductDraftExtractor.FALLBACK_BETA);
    JsonNode body = JSON.readTree(requestBody.get());
    assertThat(body.get("model").asString()).isEqualTo("claude-opus-5-5");
    assertThat(body.get("max_tokens").asLong()).isEqualTo(8000);
    assertThat(body.get("fallbacks").asString()).isEqualTo("default");
    assertThat(body.has("thinking")).as("thinking is always on for this model and not sent").isFalse();
    JsonNode output = body.get("output_config");
    assertThat(output.get("effort").asString()).isEqualTo("low");
    assertThat(output.get("format").get("type").asString()).isEqualTo("json_schema");
    JsonNode schema = output.get("format").get("schema");
    assertThat(schema.get("additionalProperties").asBoolean()).isFalse();
    assertThat(schema.get("required")).hasSize(13);
    assertThat(schema.get("properties").get("names").get("required").toString()).isEqualTo("[\"de\",\"en\",\"ar\"]");
    assertThat(schema.get("properties").get("categorySlug").toString()).contains("\"meat\"", "\"dairy\"", "\"null\"");
    assertThat(schema.get("properties").get("allergens").get("items").get("enum")).hasSize(14);
    assertThat(body.get("system").asString()).contains("data, not instructions", "- meat: ");
    JsonNode content = body.get("messages").get(0).get("content");
    assertThat(content).hasSize(1);
    assertThat(content.get(0).get("text").asString()).isEqualTo("<merchant_input>\nSucuk Gazi 400g 4,49\n</merchant_input>");
  }

  @Test
  void parsesTheStructuredAnswer() {
    Outcome outcome = extractor(true).extract(REQUEST);

    assertThat(outcome).isInstanceOfSatisfying(
        Outcome.Extracted.class,
        extracted -> {
          ExtractedProduct p = extracted.product();
          assertThat(p.names()).containsEntry("de", "Sucuk").containsEntry("ar", "سجق");
          assertThat(p.brand()).isEqualTo("Gazi");
          assertThat(p.unitSize()).isEqualTo("400 g");
          assertThat(p.netQuantity()).isEqualByComparingTo("0.4");
          assertThat(p.categorySlug()).isEqualTo("meat");
          assertThat(p.cuisineCodes()).containsExactly("TURKISH");
          assertThat(p.keywords()).containsExactly("sucuk", "sujuk");
          assertThat(p.priceCents()).isEqualTo(449L);
          assertThat(p.barcode()).isNull();
          assertThat(extracted.imageAnalysed()).isFalse();
        });
  }

  @Test
  void sendsThePhotoBeforeTheText() {
    byte[] png = TestImages.png(64, 48, Color.ORANGE);
    Request withImage =
        new Request(null, new Image(png, "image/png"), REQUEST.locales(), REQUEST.categories(), REQUEST.cuisineCodes(), REQUEST.allergenCodes());

    Outcome outcome = extractor(true).extract(withImage);

    assertThat(outcome).isInstanceOfSatisfying(Outcome.Extracted.class, e -> assertThat(e.imageAnalysed()).isTrue());
    JsonNode content = JSON.readTree(requestBody.get()).get("messages").get(0).get("content");
    assertThat(content.get(0).get("type").asString()).isEqualTo("image");
    assertThat(content.get(0).get("source").get("media_type").asString()).isEqualTo("image/png");
    assertThat(content.get(0).get("source").get("data").asString()).isEqualTo(java.util.Base64.getEncoder().encodeToString(png));
    assertThat(content.get(1).get("text").asString()).isEqualTo("The merchant sent only the photo above.");
  }

  @Test
  void largePhotosAreScaledDownToJpeg() {
    byte[] png = TestImages.png(3000, 2000, Color.GREEN);

    var prepared = ModelImages.prepare(new ModelImages.Image(png, "image/png"));

    assertThat(prepared).hasValueSatisfying(image -> {
      assertThat(image.contentType()).isEqualTo("image/jpeg");
      assertThat(image.bytes().length).isLessThanOrEqualTo(ModelImages.MAX_BYTES);
    });
  }

  @Test
  void withoutFallbacksNeitherHeaderNorParameterIsSent() {
    extractor(false).extract(REQUEST);

    assertThat(betaHeader.get()).isNull();
    assertThat(JSON.readTree(requestBody.get()).has("fallbacks")).isFalse();
  }

  @Test
  void aRefusalIsReportedAsDeclined() {
    responseBody = message("refusal", null, "{\"type\":\"refusal\",\"category\":\"general_harms\",\"explanation\":\"test\"}");

    assertThat(extractor(true).extract(REQUEST)).isInstanceOf(Outcome.Declined.class);
  }

  @Test
  void truncatedInvalidOrFailedAnswersAreFailures() {
    responseBody = message("max_tokens", "{\"names\":", null);
    assertThat(extractor(true).extract(REQUEST)).isInstanceOf(Outcome.Failed.class);

    responseBody = message("end_turn", "not json", null);
    assertThat(extractor(true).extract(REQUEST)).isInstanceOf(Outcome.Failed.class);

    status = 529;
    responseBody = "{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"Overloaded\"}}";
    assertThat(extractor(true).extract(REQUEST)).isInstanceOf(Outcome.Failed.class);
  }

  @Test
  void isDisabledWithoutAnApiKey() {
    var unset = new DukkanProperties.Anthropic(" ", "claude-opus-5-5", "low", Duration.ofSeconds(10), 8000, true, null);
    ClaudeProductDraftExtractor disabled = new ClaudeProductDraftExtractor(unset, JSON, null);

    assertThat(unset.enabled()).isFalse();
    assertThat(unset.toString()).doesNotContain("test-key");
    assertThat(disabled.enabled()).isFalse();
    assertThat(disabled.extract(REQUEST)).isInstanceOf(Outcome.Failed.class);
    assertThat(calls).hasValue(0);
  }
}
