package de.dukkan.i18n.application;

import static org.assertj.core.api.Assertions.assertThat;

import de.dukkan.i18n.infrastructure.LocaleRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LocaleResolutionTest {

  private static final List<SupportedLocale> ENABLED =
      List.of(new SupportedLocale("de", "Deutsch", "ltr"), new SupportedLocale("en", "English", "ltr"), new SupportedLocale("ar", "العربية", "rtl"));

  private final LocaleService locales =
      new LocaleService(
          new LocaleRepository(null) {
            @Override
            public List<SupportedLocale> findEnabled() {
              return ENABLED;
            }
          },
          Clock.fixed(Instant.parse("2026-10-19T08:00:00Z"), ZoneOffset.UTC));

  @ParameterizedTest
  @CsvSource({
    "ar, ar",
    "ar-EG, ar",
    "'ar-SA,ar;q=0.9,en;q=0.8', ar",
    "'fr-FR,fr;q=0.9,en;q=0.5', en",
    "'en-GB,de;q=0.7', en",
    "de-AT, de",
  })
  void resolvesTheBestEnabledLocale(String header, String expected) {
    assertThat(locales.resolve(header)).contains(expected);
  }

  @ParameterizedTest
  @CsvSource({"fr", "'tr-TR,tr;q=0.9'", "!!!invalid"})
  void unsupportedOrInvalidHeadersResolveToNothing(String header) {
    assertThat(locales.resolve(header)).isEmpty();
    assertThat(locales.contentLocale(header).effective()).isEqualTo("de");
  }

  @Test
  void missingHeaderFallsBackToGerman() {
    assertThat(locales.resolve(null)).isEmpty();
    assertThat(locales.contentLocale(null).effective()).isEqualTo("de");
    assertThat(locales.isEnabled("ar")).isTrue();
    assertThat(locales.isEnabled("fr")).isFalse();
  }

  @Test
  void contentFallsBackRequestedThenMarketPrimaryThenGermanThenAny() {
    Map<String, String> all = Map.of("de", "Äpfel", "en", "Apples", "ar", "تفاح");
    Map<String, String> noArabic = Map.of("de", "Äpfel", "en", "Apples");
    Map<String, String> englishOnly = Map.of("en", "Apples");
    Map<String, String> turkishOnly = Map.of("tr", "Elma");

    assertThat(ContentLocale.of("ar").pick(all, "de")).isEqualTo("تفاح");
    assertThat(ContentLocale.of("ar").pick(noArabic, "en")).isEqualTo("Apples");
    assertThat(ContentLocale.of("ar").pick(noArabic, "de")).isEqualTo("Äpfel");
    assertThat(ContentLocale.of(null).pick(noArabic, "en")).isEqualTo("Apples");
    assertThat(ContentLocale.of("ar").pick(englishOnly, "de")).isEqualTo("Apples");
    assertThat(ContentLocale.of("de").pick(turkishOnly)).isEqualTo("Elma");
    assertThat(ContentLocale.of("de").<String>pick(Map.of())).isNull();
    assertThat(ContentLocale.of("ar").chain("en")).containsExactly("ar", "en", "de");
    assertThat(ContentLocale.of("de").chain("de")).containsExactly("de");
    assertThat(ContentLocale.of(null).chain("ar")).containsExactly("ar", "de");
  }
}
