package de.dukkan.i18n.api;

import de.dukkan.i18n.application.LocaleService;
import de.dukkan.i18n.application.SupportedLocale;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/v1")
@RestController
final class LocaleController {

  private final LocaleService locales;

  LocaleController(LocaleService locales) {
    this.locales = locales;
  }

  @GetMapping("/locales")
  List<SupportedLocale> locales() {
    return locales.enabled();
  }
}
