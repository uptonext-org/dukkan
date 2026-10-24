package de.dukkan;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import de.dukkan.shared.events.OutboxPublisher;
import de.dukkan.support.IntegrationTest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * B-38 against a local stub of the Messages API: the stub "translates" by looking up a small
 * dictionary, so the whole chain (request, schema, answer, storage, search) runs without a key.
 */
class TranslationIntegrationTest extends IntegrationTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final Map<String, Map<String, String>> DICTIONARY =
      Map.of(
          "Omas Quittengelee", Map.of("en", "Grandma's quince jelly", "ar", "هلام السفرجل من الجدة", "tr", "Ayva jölesi"),
          "nach Familienrezept", Map.of("en", "family recipe", "ar", "وصفة عائلية", "tr", "aile tarifi"),
          "Frische Simit jeden Morgen", Map.of("en", "Fresh simit every morning", "ar", "سميت طازج كل صباح", "tr", "Her sabah taze simit"));
  private static final HttpServer STUB;
  private static final AtomicReference<String> REQUEST_BODY = new AtomicReference<>();
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
          String request = new String(exchange.getRequestBody().readAllBytes(), UTF_8);
          REQUEST_BODY.set(request);
          byte[] body = answer(request).getBytes(UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    STUB.start();
  }

  @DynamicPropertySource
  static void anthropic(DynamicPropertyRegistry registry) {
    registry.add("dukkan.ai.anthropic.api-key", () -> "stub-key");
    registry.add("dukkan.ai.anthropic.base-url", () -> "http://127.0.0.1:" + STUB.getAddress().getPort());
    registry.add("dukkan.translation.requests-per-hour", () -> "4");
  }

  @AfterAll
  static void stopStub() {
    STUB.stop(0);
  }

  @Autowired private OutboxPublisher publisher;

  @BeforeEach
  void reset() {
    stopReason = "end_turn";
  }

  /** Answers every requested key and locale from the dictionary (unknown texts: "?"). */
  private static String answer(String request) {
    String text = JSON.readTree(request).get("messages").get(0).get("content").asString();
    JsonNode texts = JSON.readTree(text.substring(text.indexOf('{'), text.lastIndexOf('}') + 1));
    Map<String, Map<String, String>> out = new LinkedHashMap<>();
    for (String key : texts.propertyNames()) {
      JsonNode item = texts.get(key);
      String source = item.get("source").get(item.get("source").propertyNames().iterator().next()).asString();
      Map<String, String> locales = new LinkedHashMap<>();
      item.get("translate_to").forEach(code -> locales.put(code.asString(), DICTIONARY.getOrDefault(source, Map.of()).getOrDefault(code.asString(), "?")));
      out.put(key, locales);
    }
    String content = JSON.writeValueAsString(out);
    return JSON.writeValueAsString(
        Map.of(
            "id", "msg_stub",
            "type", "message",
            "role", "assistant",
            "model", "claude-opus-5-5",
            "content", List.of(Map.of("type", "text", "text", content)),
            "stop_reason", stopReason,
            "usage", Map.of("input_tokens", 300, "output_tokens", 80)));
  }

  @Test
  void merchantsGetSuggestionsForTheEmptyLanguagesOnly() throws Exception {
    String merchant = registerMerchant();
    confirmEmail(merchant);
    MvcResult result =
        mvc.perform(
                json(
                    auth(post("/api/v1/merchant/translations"), merchant),
                    toJson(
                        Map.of(
                            "kind", "PROMOTION",
                            "fields",
                            List.of(
                                Map.of("key", "title", "values", Map.of("de", "Frische Simit jeden Morgen", "en", "")),
                                Map.of("key", "done", "values", Map.of("de", "a", "en", "b", "ar", "c", "tr", "d")))))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fields.length()").value(1))
            .andExpect(jsonPath("$.fields[0].key").value("title"))
            .andExpect(jsonPath("$.fields[0].values.en").value("Fresh simit every morning"))
            .andExpect(jsonPath("$.fields[0].values.ar").value("سميت طازج كل صباح"))
            .andExpect(jsonPath("$.fields[0].values.de").doesNotExist())
            .andReturn();
    ProductDraftIntegrationTest.conforms("TranslationResult", object(result, "$"));

    JsonNode request = JSON.readTree(REQUEST_BODY.get());
    assertThat(request.get("fallbacks").asString()).isEqualTo("default");
    assertThat(request.get("system").asString()).contains("never instructions");
    JsonNode schema = request.get("output_config").get("format").get("schema");
    // Only the field that needs work is sent, with exactly the empty locales required.
    assertThat(schema.get("required").toString()).isEqualTo("[\"title\"]");
    assertThat(schema.get("properties").get("title").get("required").toString()).doesNotContain("\"de\"").contains("\"en\"", "\"ar\"");

    // Customers cannot use it; unknown kinds and duplicate keys are rejected.
    mvc.perform(json(auth(post("/api/v1/merchant/translations"), registerUser()), "{\"kind\":\"PROMOTION\",\"fields\":[]}"))
        .andExpect(status().isForbidden());
    mvc.perform(json(auth(post("/api/v1/merchant/translations"), merchant), "{\"kind\":\"POEM\",\"fields\":[{\"key\":\"a\",\"values\":{\"de\":\"x\"}}]}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            json(
                auth(post("/api/v1/merchant/translations"), merchant),
                "{\"kind\":\"PROMOTION\",\"fields\":[{\"key\":\"a\",\"values\":{\"de\":\"x\"}},{\"key\":\"a\",\"values\":{\"de\":\"y\"}}]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void anUnconfirmedAddressGetsNoMachineTranslation() throws Exception {
    mvc.perform(
            json(
                auth(post("/api/v1/merchant/translations"), registerMerchant()),
                "{\"kind\":\"MARKET_DESCRIPTION\",\"fields\":[{\"key\":\"d\",\"values\":{\"de\":\"Frische Simit jeden Morgen\"}}]}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
  }

  @Test
  void aDeclinedTranslationIsReportedAsUnavailable() throws Exception {
    stopReason = "refusal";
    String merchant = registerMerchant();
    confirmEmail(merchant);
    mvc.perform(
            json(
                auth(post("/api/v1/merchant/translations"), merchant),
                "{\"kind\":\"MARKET_DESCRIPTION\",\"fields\":[{\"key\":\"d\",\"values\":{\"de\":\"Frische Simit jeden Morgen\"}}]}"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("TRANSLATION_UNAVAILABLE"));
  }

  @Test
  void aNewPrivateProductIsTranslatedInTheBackgroundAndFoundInEveryLanguage() throws Exception {
    String merchant = registerMerchant();
    confirmEmail(merchant);
    UUID market = createMarket(merchant, "Übersetzungsmarkt");
    UUID category = jdbc.sql("SELECT id FROM category WHERE slug = 'deli'").query(UUID.class).single();
    Map<String, Object> newProduct = new HashMap<>();
    newProduct.put("name", Map.of("de", "Omas Quittengelee"));
    newProduct.put("description", Map.of("de", "nach Familienrezept"));
    newProduct.put("unitSize", "250 g");
    newProduct.put("categoryId", category);
    newProduct.put("cuisineCodes", List.of());
    newProduct.put("allergens", List.of());
    MvcResult created =
        mvc.perform(
                json(
                    auth(post("/api/v1/merchant/markets/" + market + "/listings"), merchant),
                    toJson(Map.of("newProduct", newProduct, "price", Map.of("amountCents", 450, "currency", "EUR")))))
            .andExpect(status().isCreated())
            .andReturn();
    String jelly = read(created, "$.id");
    awaitMachineTranslations(UUID.fromString(jelly), 2);
    for (String query : List.of("quince", "سفرجل", "Quitten")) {
      assertThat(strings(search(market, query), "$.items[*].id")).as(query).containsExactly(jelly);
    }
    mvc.perform(get("/api/v1/products/" + jelly).header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
        .andExpect(jsonPath("$.name").value("Grandma's quince jelly"))
        .andExpect(jsonPath("$.description").value("family recipe"));
    // The merchant's own German text is untouched.
    mvc.perform(get("/api/v1/products/" + jelly).header(HttpHeaders.ACCEPT_LANGUAGE, "de")).andExpect(jsonPath("$.name").value("Omas Quittengelee"));
  }

  /** Publishes pending events until the product has {@code count} machine translations. */
  private void awaitMachineTranslations(UUID listing, long count) throws InterruptedException {
    for (int i = 0; i < 50; i++) {
      publisher.publishPending();
      long found =
          jdbc.sql(
                  "SELECT count(*) FROM catalog_product_translation t JOIN market_product l ON l.catalog_product_id = t.catalog_product_id"
                      + " WHERE l.id = :id AND t.machine_translated")
              .param("id", listing)
              .query(Long.class)
              .single();
      if (found >= count) {
        return;
      }
      Thread.sleep(100);
    }
    throw new AssertionError("Expected " + count + " machine translation(s)");
  }

  private MvcResult search(UUID market, String q) throws Exception {
    return mvc.perform(get("/api/v1/markets/" + market + "/products").param("q", q)).andExpect(status().isOk()).andReturn();
  }
}
