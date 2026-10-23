package de.dukkan.legal.api;

import de.dukkan.legal.application.PlatformSettings;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public platform facts the apps show: legal links, terms version to accept, payment mode. */
@RestController
final class PlatformController {

  record LegalLinks(@Nullable String imprintUrl, @Nullable String privacyUrl, @Nullable String termsUrl, @Nullable String termsVersion) {}

  record PlatformResponse(LegalLinks legal, @Nullable String supportEmail, PlatformSettings.PaymentMode payment) {}

  private final PlatformSettings settings;

  PlatformController(PlatformSettings settings) {
    this.settings = settings;
  }

  @GetMapping("/api/v1/platform")
  ResponseEntity<PlatformResponse> platform() {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
        .body(
            new PlatformResponse(
                new LegalLinks(settings.imprintUrl(), settings.privacyUrl(), settings.termsUrl(), settings.termsVersion()),
                settings.supportEmail(),
                settings.payment()));
  }
}
