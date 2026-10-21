package de.dukkan.translation.application;

import de.dukkan.catalog.application.CatalogService;
import de.dukkan.catalog.application.PrivateProductCreated;
import de.dukkan.catalog.application.PrivateProductTexts;
import de.dukkan.i18n.application.LocaleService;
import de.dukkan.i18n.application.SupportedLocale;
import de.dukkan.shared.events.EventHandler;
import de.dukkan.shared.events.OutboxEvent;
import de.dukkan.translation.application.Translator.Item;
import de.dukkan.translation.application.Translator.TextKind;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Fills the languages a merchant left empty on a new private product (B-38), so shoppers find it in
 * every language. Translations are marked as machine-made and never overwrite the merchant's text.
 * Without a translator, or when it fails, the product simply stays in the merchant's languages.
 */
@Component
class PrivateProductTranslationHandler implements EventHandler {

  private static final Logger LOG = LoggerFactory.getLogger(PrivateProductTranslationHandler.class);

  private final Translator translator;
  private final CatalogService catalog;
  private final LocaleService locales;
  private final JsonMapper json;

  PrivateProductTranslationHandler(Translator translator, CatalogService catalog, LocaleService locales, JsonMapper json) {
    this.translator = translator;
    this.catalog = catalog;
    this.locales = locales;
    this.json = json;
  }

  @Override
  public String name() {
    return "private-product-translation";
  }

  @Override
  public Set<String> types() {
    return Set.of(PrivateProductCreated.TYPE);
  }

  @Override
  public void handle(OutboxEvent event) {
    if (!translator.enabled()) {
      return;
    }
    PrivateProductCreated created = json.readValue(event.payload(), PrivateProductCreated.class);
    Optional<PrivateProductTexts> found = catalog.privateProductTexts(created.catalogProductId());
    if (found.isEmpty()) {
      return;
    }
    PrivateProductTexts texts = found.get();
    List<String> targets = locales.enabled().stream().map(SupportedLocale::code).filter(code -> !texts.names().containsKey(code)).toList();
    if (targets.isEmpty()) {
      return;
    }
    Optional<Map<String, Map<String, String>>> names = translator.translate(TextKind.PRODUCT_NAME, List.of(new Item("name", texts.names(), targets)));
    Optional<Map<String, Map<String, String>>> descriptions =
        texts.descriptions().isEmpty()
            ? Optional.of(Map.of())
            : translator.translate(TextKind.PRODUCT_DESCRIPTION, List.of(new Item("description", texts.descriptions(), targets)));
    if (names.isEmpty()) {
      LOG.info("No machine translation for product {} (translator unavailable)", created.catalogProductId());
      return;
    }
    int added =
        catalog.addMachineTranslations(
            created.catalogProductId(),
            names.orElseThrow().getOrDefault("name", Map.of()),
            descriptions.orElseGet(Map::of).getOrDefault("description", Map.of()));
    LOG.info("Added {} machine translation(s) to product {}", added, created.catalogProductId());
  }
}
