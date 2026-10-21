package de.dukkan.i18n.application;

import de.dukkan.i18n.infrastructure.UiTextRepository;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Short server-generated texts from table {@code ui_text} (home section titles, promotion badge
 * templates with {@code {placeholders}}). Loaded once; falls back requested → de → any → key.
 */
@Service
public class UiTexts {

  private final UiTextRepository repository;
  private final AtomicReference<@Nullable Map<String, Map<String, String>>> cache = new AtomicReference<>();

  public UiTexts(UiTextRepository repository) {
    this.repository = repository;
  }

  public String text(String key, ContentLocale locale) {
    Map<String, String> byLocale = texts().getOrDefault(key, Map.of());
    String text = locale.pick(byLocale);
    return text == null ? key : text;
  }

  /** Fills {@code {name}} placeholders of the template stored under {@code key}. */
  public String format(String key, ContentLocale locale, Map<String, String> arguments) {
    String text = text(key, locale);
    for (Map.Entry<String, String> argument : arguments.entrySet()) {
      text = text.replace("{" + argument.getKey() + "}", argument.getValue());
    }
    return text;
  }

  private Map<String, Map<String, String>> texts() {
    Map<String, Map<String, String>> texts = cache.get();
    if (texts == null) {
      texts = repository.findAll();
      cache.set(texts);
    }
    return texts;
  }
}
