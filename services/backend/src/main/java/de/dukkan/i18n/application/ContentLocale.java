package de.dukkan.i18n.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * The content locale of a request. {@code requested} is the best enabled match for {@code
 * Accept-Language} (null if none matched). Content falls back requested → market primary locale →
 * {@code de} → any existing translation.
 */
public record ContentLocale(@Nullable String requested) {

  public static final String DEFAULT = "de";

  /** Request attribute under which {@code ContentLocaleFilter} stores the resolved locale. */
  public static final String REQUEST_ATTRIBUTE = "dukkan.contentLocale";

  public static ContentLocale of(@Nullable String requested) {
    return new ContentLocale(requested);
  }

  /** The value of {@code Content-Language}: the requested locale, or {@code de}. */
  public String effective() {
    return requested == null ? DEFAULT : requested;
  }

  /** Preferred locales in order, without duplicates. */
  public List<String> chain(@Nullable String primaryLocale) {
    List<String> chain = new ArrayList<>(3);
    for (String code : new String[] {requested, primaryLocale, DEFAULT}) {
      if (code != null && !chain.contains(code)) {
        chain.add(code);
      }
    }
    return chain;
  }

  /** Picks the best translation from {@code byLocale} (any translation as last resort). */
  public <T> @Nullable T pick(Map<String, T> byLocale, @Nullable String primaryLocale) {
    if (byLocale.isEmpty()) {
      return null;
    }
    for (String code : chain(primaryLocale)) {
      T value = byLocale.get(code);
      if (value != null) {
        return value;
      }
    }
    return new TreeMap<>(byLocale).firstEntry().getValue();
  }

  /** Like {@link #pick(Map, String)} without a market context. */
  public <T> @Nullable T pick(Map<String, T> byLocale) {
    return pick(byLocale, null);
  }
}
