package de.dukkan.i18n.application;

import de.dukkan.i18n.infrastructure.LocaleRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Enabled locales (table {@code supported_locale}) and {@code Accept-Language} resolution. The list
 * is cached for a minute, so enabling a language is a data change only.
 */
@Service
public class LocaleService {

  private static final Duration CACHE_TTL = Duration.ofMinutes(1);

  private record Cached(List<SupportedLocale> locales, Instant loadedAt) {}

  private final LocaleRepository repository;
  private final Clock clock;
  private final AtomicReference<@Nullable Cached> cache = new AtomicReference<>();

  public LocaleService(LocaleRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  /** Enabled locales in display order. */
  public List<SupportedLocale> enabled() {
    Cached cached = cache.get();
    Instant now = clock.instant();
    if (cached == null || cached.loadedAt().plus(CACHE_TTL).isBefore(now) || now.isBefore(cached.loadedAt())) {
      cached = new Cached(repository.findEnabled(), now);
      cache.set(cached);
    }
    return cached.locales();
  }

  public boolean isEnabled(@Nullable String code) {
    return code != null && enabled().stream().anyMatch(l -> l.code().equals(code));
  }

  /** Best enabled locale for an {@code Accept-Language} header (RFC 4647 lookup), if any. */
  public Optional<String> resolve(@Nullable String acceptLanguage) {
    if (acceptLanguage == null || acceptLanguage.isBlank()) {
      return Optional.empty();
    }
    List<Locale.LanguageRange> ranges;
    try {
      ranges = Locale.LanguageRange.parse(acceptLanguage);
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
    List<String> codes = enabled().stream().map(SupportedLocale::code).toList();
    return Optional.ofNullable(Locale.lookupTag(ranges, codes));
  }

  public ContentLocale contentLocale(@Nullable String acceptLanguage) {
    return ContentLocale.of(resolve(acceptLanguage).orElse(null));
  }
}
