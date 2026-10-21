package de.dukkan.translation.application;

import de.dukkan.i18n.application.LocaleService;
import de.dukkan.i18n.application.SupportedLocale;
import de.dukkan.shared.config.DukkanProperties;
import de.dukkan.shared.error.BusinessRuleException;
import de.dukkan.shared.error.ErrorCodes;
import de.dukkan.shared.error.InvalidRequestException;
import de.dukkan.shared.ratelimit.FixedWindowRateLimiter;
import de.dukkan.translation.application.Translator.Item;
import de.dukkan.translation.application.Translator.TextKind;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Machine translation of merchant texts into the enabled locales that are still empty (B-38). The
 * merchant reviews the suggestions before saving; nothing is stored here.
 */
@Service
public class TranslationService {

  public static final int MAX_FIELDS = 20;
  public static final int MAX_CHARS = 8000;

  /** One field to complete: locale → text; missing locales are filled. */
  public record Field(String key, Map<String, String> values) {}

  private final Translator translator;
  private final LocaleService locales;
  private final FixedWindowRateLimiter<UUID> rateLimiter;

  public TranslationService(Translator translator, LocaleService locales, DukkanProperties properties, Clock clock) {
    this.translator = translator;
    this.locales = locales;
    this.rateLimiter = new FixedWindowRateLimiter<>(properties.translation().requestsPerHour(), Duration.ofHours(1), clock);
  }

  public boolean available() {
    return translator.enabled();
  }

  /** Returns only the newly translated locales per field; fields that are complete are left out. */
  public List<Field> complete(UUID userId, TextKind kind, List<Field> fields) {
    if (!translator.enabled()) {
      throw new BusinessRuleException(ErrorCodes.TRANSLATION_UNAVAILABLE, "Machine translation is not available.");
    }
    int chars = fields.stream().mapToInt(f -> f.values().values().stream().filter(v -> v != null).mapToInt(String::length).sum()).sum();
    if (fields.stream().map(Field::key).distinct().count() != fields.size()) {
      throw new InvalidRequestException("fields", "Unique", "field keys must be unique");
    }
    if (fields.size() > MAX_FIELDS || chars > MAX_CHARS) {
      throw new InvalidRequestException("fields", "Size", "at most " + MAX_FIELDS + " fields and " + MAX_CHARS + " characters");
    }
    List<String> enabled = locales.enabled().stream().map(SupportedLocale::code).toList();
    List<Item> items = new ArrayList<>();
    for (Field field : fields) {
      Map<String, String> source = new LinkedHashMap<>();
      field.values().forEach((code, text) -> {
        if (enabled.contains(code) && text != null && !text.isBlank()) {
          source.put(code, text.strip());
        }
      });
      List<String> targets = enabled.stream().filter(code -> !source.containsKey(code)).toList();
      if (!source.isEmpty() && !targets.isEmpty()) {
        items.add(new Item(field.key(), source, targets));
      }
    }
    if (items.isEmpty()) {
      return List.of();
    }
    rateLimiter.acquire(userId, "translations");
    Map<String, Map<String, String>> result =
        translator.translate(kind, items).orElseThrow(() -> new BusinessRuleException(ErrorCodes.TRANSLATION_UNAVAILABLE, "The translation failed. Please try again."));
    List<Field> out = new ArrayList<>();
    for (Item item : items) {
      Map<String, String> translated = new LinkedHashMap<>();
      Map<String, String> values = result.getOrDefault(item.key(), Map.of());
      int max = kind == TextKind.PRODUCT_NAME ? 200 : 2000;
      for (String target : item.targets()) {
        String text = values.get(target);
        if (text != null && !text.isBlank()) {
          String stripped = text.strip();
          translated.put(target, stripped.length() <= max ? stripped : stripped.substring(0, max).strip());
        }
      }
      if (!translated.isEmpty()) {
        out.add(new Field(item.key(), translated));
      }
    }
    return out;
  }
}
