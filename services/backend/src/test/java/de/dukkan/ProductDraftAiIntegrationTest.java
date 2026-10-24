package de.dukkan;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import de.dukkan.support.TestImages;
import java.awt.Color;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * B-33 with a language model: the Claude extractor is configured against a local stub of the
 * Messages API, so the whole chain (photo upload, SDK request, structured answer, sanitising,
 * catalog matches, fallback to the rules) runs without a real key.
 */
class ProductDraftAiIntegrationTest extends ProductDraftIntegrationTest {

  private static final HttpServer STUB;
  private static final AtomicReference<String> REQUEST_BODY = new AtomicReference<>();
  private static volatile int status = 200;
  private static volatile String answer = "";

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
          byte[] body = answer.getBytes(UTF_8);
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
    registry.add("dukkan.capture.drafts-per-hour", () -> "8");
  }

  @AfterAll
  static void stopStub() {
    STUB.stop(0);
  }

  /** The model's answer to every request: unknown codes and an over-long keyword must be dropped. */
  private static final String SUCUK =
      """
      {"names":{"de":"Sucuk","en":"Sucuk beef sausage","ar":"سجق تركي"},
       "descriptions":{"de":"Würzige Rinderwurst mit Knoblauch.","en":"Spicy beef sausage with garlic.","ar":""},
       "brand":"Gazi","unitSize":"400 g","netQuantity":0.4,"netUnit":"kg","categorySlug":"meat",
       "cuisineCodes":["TURKISH","KLINGON"],"organic":false,"allergens":["mustard","glitter"],
       "keywords":["sucuk","sujuk","%s"],"priceCents":null,"barcode":"8690000000001"}
      """
          .formatted("x".repeat(61));

  private static String message(String stopReason, String text) {
    String content = text.isEmpty() ? "[]" : "[{\"type\":\"text\",\"text\":" + quoted(text) + "}]";
    return "{\"id\":\"msg_stub\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"claude-opus-5-5\",\"content\":"
        + content
        + ",\"stop_reason\":\""
        + stopReason
        + "\",\"stop_sequence\":null,\"usage\":{\"input_tokens\":900,\"output_tokens\":300}}";
  }

  private static String quoted(String text) {
    return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
  }

  @BeforeEach
  void answerWithSucuk() {
    status = 200;
    answer = message("end_turn", SUCUK);
  }

  @Override
  @Test
  void parsesAGermanNoteAndFindsTheSharedCatalogProduct() throws Exception {
    String merchant = registerMerchant();
    confirmEmail(merchant);
    UUID market = createMarket(merchant, "KI-Markt");

    MvcResult result =
        draft(merchant, market, "{\"text\":\"Sucuk Gazi 400 g 4,49 €\"}", "de")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.engine").value("AI"))
            .andExpect(jsonPath("$.product.name.de").value("Sucuk"))
            .andExpect(jsonPath("$.product.name.ar").value("سجق تركي"))
            .andExpect(jsonPath("$.product.description.ar").doesNotExist())
            .andExpect(jsonPath("$.product.brand").value("Gazi"))
            .andExpect(jsonPath("$.product.categoryId").value(MEAT))
            .andExpect(jsonPath("$.product.barcode").value("8690000000001"))
            .andExpect(jsonPath("$.price.amountCents").value(449))
            .andExpect(jsonPath("$.catalogMatches[0].name").value("Sucuk"))
            .andReturn();
    assertThat(strings(result, "$.product.cuisineCodes[*]")).containsExactly("TURKISH");
    assertThat(strings(result, "$.product.allergens[*]")).containsExactly("mustard");
    assertThat(strings(result, "$.product.keywords[*]")).containsExactly("sucuk", "sujuk");
    assertThat(strings(result, "$.warnings[*]")).isEmpty();
    conforms("ProductDraft", object(result, "$"));
    conforms("ProductDraftProduct", object(result, "$.product"));

    JsonNode request = json.readTree(REQUEST_BODY.get());
    assertThat(request.get("fallbacks").asString()).isEqualTo("default");
    assertThat(request.get("messages").get(0).get("content").get(0).get("text").asString()).contains("Sucuk Gazi 400 g 4,49 €");
    assertThat(request.get("output_config").get("format").get("schema").get("properties").get("names").get("required").toString())
        .isEqualTo("[\"de\",\"en\",\"ar\"]");
  }

  @Override
  @Test
  void aPhotoWithoutLanguageModelIsKeptButNotAnalysed() throws Exception {
    String merchant = registerMerchant();
    confirmEmail(merchant);
    UUID market = createMarket(merchant, "KI-Fotomarkt");
    String key = uploadPhoto(merchant, market, TestImages.png(48, 32, new Color(0x7b2d26)));

    MvcResult result =
        draft(merchant, market, "{\"imageKey\":\"" + key + "\"}", "en")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.engine").value("AI"))
            .andExpect(jsonPath("$.product.name.en").value("Sucuk beef sausage"))
            .andExpect(jsonPath("$.product.imageUrl").value("http://localhost/api/v1/media/" + key))
            .andReturn();
    assertThat(strings(result, "$.warnings[*]")).containsExactly("PRICE_MISSING");
    JsonNode content = json.readTree(REQUEST_BODY.get()).get("messages").get(0).get("content");
    assertThat(content.get(0).get("type").asString()).isEqualTo("image");
    assertThat(content.get(0).get("source").get("media_type").asString()).isEqualTo("image/png");
  }

  /** The model left out the price; the one the merchant typed is taken from the rule-based parser. */
  @Override
  @Test
  void arabicTextYieldsAnArabicNameAndArabicMatches() throws Exception {
    String merchant = registerMerchant();
    confirmEmail(merchant);
    UUID market = createMarket(merchant, "سوق الذكاء");

    draft(merchant, market, "{\"text\":\"سجق ٤٠٠ غ ٤٫٤٩\"}", "ar")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.engine").value("AI"))
        .andExpect(jsonPath("$.product.name.ar").value("سجق تركي"))
        .andExpect(jsonPath("$.price.amountCents").value(449))
        .andExpect(jsonPath("$.catalogMatches[0].name").value("سجق تركي"));
  }

  @Test
  void fallsBackToTheRulesWhenTheModelFailsOrDeclines() throws Exception {
    String merchant = registerMerchant();
    confirmEmail(merchant);
    UUID market = createMarket(merchant, "Ausfallmarkt");

    status = 500;
    answer = "{\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":\"boom\"}}";
    MvcResult failed =
        draft(merchant, market, "{\"text\":\"Halloumi 250 g 3,29 €\"}", "de")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.engine").value("RULES"))
            .andExpect(jsonPath("$.product.name.de").value("Halloumi"))
            .andExpect(jsonPath("$.price.amountCents").value(329))
            .andReturn();
    assertThat(strings(failed, "$.warnings[*]")).containsExactly("AI_UNAVAILABLE");

    status = 200;
    answer = message("refusal", "");
    MvcResult declined = draft(merchant, market, "{\"text\":\"Halloumi 250 g\"}", "de").andExpect(status().isOk()).andReturn();
    assertThat((String) read(declined, "$.engine")).isEqualTo("RULES");
    assertThat(strings(declined, "$.warnings[*]")).containsExactly("AI_DECLINED", "PRICE_MISSING");
  }

  @Test
  void limitsDraftsPerMerchantAndHour() throws Exception {
    String merchant = registerMerchant();
    confirmEmail(merchant);
    UUID market = createMarket(merchant, "Limitmarkt");
    for (int i = 0; i < 8; i++) {
      draft(merchant, market, "{\"text\":\"Ayran\"}", "de").andExpect(status().isOk());
    }
    draft(merchant, market, "{\"text\":\"Ayran\"}", "de")
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    String other = registerMerchant();
    confirmEmail(other);
    draft(other, createMarket(other, "Anderer Limitmarkt"), "{\"text\":\"Ayran\"}", "de").andExpect(status().isOk());
  }
}
