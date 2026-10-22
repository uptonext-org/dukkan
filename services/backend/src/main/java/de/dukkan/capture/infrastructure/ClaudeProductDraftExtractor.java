package de.dukkan.capture.infrastructure;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import de.dukkan.capture.application.ProductDraftExtractor;
import de.dukkan.capture.domain.ExtractedProduct;
import de.dukkan.shared.ai.AiUsageRecorder;
import de.dukkan.shared.ai.AnthropicClients;
import de.dukkan.shared.config.DukkanProperties;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link ProductDraftExtractor} on the Claude API (Messages API, official Java SDK). One request per
 * draft: the photo (if any) and the merchant's text, answered as JSON that follows a schema
 * ({@code output_config.format}), so the answer always parses. Enabled when {@code
 * dukkan.ai.anthropic.api-key} is set.
 *
 * <ul>
 *   <li>Effort {@code low} by default: reading a label is not a hard problem, and it keeps latency
 *       and cost down. Thinking is adaptive and always on for this model, so it is not configured.
 *   <li>Server-side fallbacks ({@code fallbacks: "default"}, beta header {@value #FALLBACK_BETA}):
 *       when the model declines, the API retries on the model it recommends for that case. A
 *       remaining {@code refusal} stop reason is reported as {@link Outcome.Declined}.
 *   <li>The merchant's text is untrusted: the system prompt treats it as product data only.
 * </ul>
 */
@Component
final class ClaudeProductDraftExtractor implements ProductDraftExtractor {

  static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

  private static final Logger LOG = LoggerFactory.getLogger(ClaudeProductDraftExtractor.class);

  private final DukkanProperties.Anthropic settings;
  private final JsonMapper json;
  private final @Nullable AnthropicClient client;
  private final AiUsageRecorder usage;

  @Autowired
  ClaudeProductDraftExtractor(AnthropicClients clients, JsonMapper json, AiUsageRecorder usage) {
    this(clients.settings(), json, clients.client().orElse(null), usage);
  }

  ClaudeProductDraftExtractor(DukkanProperties.Anthropic settings, JsonMapper json, @Nullable AnthropicClient client) {
    this(settings, json, client, AiUsageRecorder.NONE);
  }

  private ClaudeProductDraftExtractor(
      DukkanProperties.Anthropic settings, JsonMapper json, @Nullable AnthropicClient client, AiUsageRecorder usage) {
    this.settings = settings;
    this.json = json;
    this.client = client;
    this.usage = usage;
  }

  @Override
  public boolean enabled() {
    return client != null;
  }

  @Override
  public Outcome extract(Request request) {
    if (client == null) {
      return new Outcome.Failed();
    }
    Optional<Image> image = request.image() == null ? Optional.empty() : ModelImages.prepare(request.image());
    if (request.text() == null && image.isEmpty()) {
      return new Outcome.Failed();
    }
    long started = System.nanoTime();
    try {
      Message message = client.messages().create(params(request, image.orElse(null)));
      LOG.info(
          "Claude product draft: stop {}, {} input / {} output tokens, {} ms",
          message.stopReason().map(StopReason::asString).orElse("none"),
          message.usage().inputTokens(),
          message.usage().outputTokens(),
          (System.nanoTime() - started) / 1_000_000);
      Outcome outcome = outcome(message, image.isPresent());
      usage.record(
          "capture",
          outcome instanceof Outcome.Extracted ? "ok" : outcome instanceof Outcome.Declined ? "declined" : "incomplete",
          message.usage().inputTokens(),
          message.usage().outputTokens(),
          Duration.ofNanos(System.nanoTime() - started));
      return outcome;
    } catch (RuntimeException e) {
      // SDK errors (AnthropicException: HTTP status, timeout, I/O) and invalid JSON alike: the
      // caller falls back to the rule-based parser. The merchant's input is not logged.
      LOG.warn("Claude product draft failed: {}", e.toString());
      usage.record("capture", "error", 0, 0, Duration.ofNanos(System.nanoTime() - started));
      return new Outcome.Failed();
    }
  }

  Outcome outcome(Message message, boolean imageAnalysed) {
    StopReason stop = message.stopReason().orElse(null);
    if (StopReason.REFUSAL.equals(stop)) {
      message.stopDetails().ifPresent(details -> LOG.info("Claude product draft declined: {}", details));
      return new Outcome.Declined();
    }
    if (!StopReason.END_TURN.equals(stop)) {
      return new Outcome.Failed();
    }
    String text = message.content().stream().flatMap(block -> block.text().stream()).map(TextBlock::text).collect(Collectors.joining());
    try {
      return new Outcome.Extracted(json.readValue(text, ModelDraft.class).toExtracted(), imageAnalysed);
    } catch (JacksonException e) {
      LOG.warn("Claude product draft: answer is not valid JSON ({})", e.getOriginalMessage());
      return new Outcome.Failed();
    }
  }

  MessageCreateParams params(Request request, @Nullable Image image) {
    List<ContentBlockParam> content = new ArrayList<>();
    if (image != null) {
      content.add(
          ContentBlockParam.ofImage(
              Base64ImageSource.builder()
                  .data(Base64.getEncoder().encodeToString(image.bytes()))
                  .mediaType(mediaType(image.contentType()))
                  .build()));
    }
    content.add(
        ContentBlockParam.ofText(
            request.text() == null
                ? "The merchant sent only the photo above."
                : "<merchant_input>\n" + request.text() + "\n</merchant_input>"));
    MessageCreateParams.Builder builder =
        MessageCreateParams.builder()
            .model(settings.model())
            .maxTokens(settings.maxTokens())
            .system(DraftPrompt.system(request))
            .outputConfig(
                OutputConfig.builder()
                    .effort(OutputConfig.Effort.of(settings.effort()))
                    .format(
                        JsonOutputFormat.builder()
                            .schema(JsonOutputFormat.Schema.builder().putAllAdditionalProperties(jsonValues(DraftPrompt.schema(request))).build())
                            .build())
                    .build())
            .addUserMessageOfBlockParams(content);
    if (settings.serverSideFallbacks()) {
      builder.putAdditionalHeader("anthropic-beta", FALLBACK_BETA).putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
    }
    return builder.build();
  }

  private static Base64ImageSource.MediaType mediaType(String contentType) {
    return switch (contentType) {
      case "image/png" -> Base64ImageSource.MediaType.IMAGE_PNG;
      case "image/webp" -> Base64ImageSource.MediaType.IMAGE_WEBP;
      default -> Base64ImageSource.MediaType.IMAGE_JPEG;
    };
  }

  private static Map<String, JsonValue> jsonValues(Map<String, Object> schema) {
    Map<String, JsonValue> out = new LinkedHashMap<>();
    schema.forEach((key, value) -> out.put(key, JsonValue.from(value)));
    return out;
  }

  /** The model's answer, shaped by {@link DraftPrompt#schema}. */
  record ModelDraft(
      @Nullable Map<String, String> names,
      @Nullable Map<String, String> descriptions,
      @Nullable String brand,
      @Nullable String unitSize,
      @Nullable BigDecimal netQuantity,
      @Nullable String netUnit,
      @Nullable String categorySlug,
      @Nullable List<String> cuisineCodes,
      @Nullable Boolean organic,
      @Nullable List<String> allergens,
      @Nullable List<String> keywords,
      @Nullable Long priceCents,
      @Nullable String barcode) {

    ExtractedProduct toExtracted() {
      return new ExtractedProduct(
          names == null ? Map.of() : withoutNulls(names),
          descriptions == null ? Map.of() : withoutNulls(descriptions),
          brand,
          unitSize,
          netQuantity,
          netUnit,
          categorySlug,
          cuisineCodes == null ? List.of() : withoutNulls(cuisineCodes),
          Boolean.TRUE.equals(organic),
          allergens == null ? List.of() : withoutNulls(allergens),
          keywords == null ? List.of() : withoutNulls(keywords),
          priceCents,
          barcode);
    }

    private static Map<String, String> withoutNulls(Map<String, String> map) {
      Map<String, String> out = new LinkedHashMap<>();
      map.forEach(
          (k, v) -> {
            if (k != null && v != null) {
              out.put(k, v);
            }
          });
      return out;
    }

    private static List<String> withoutNulls(List<String> list) {
      return list.stream().filter(Objects::nonNull).toList();
    }
  }
}
