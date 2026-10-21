package de.dukkan.catalog.application;

import de.dukkan.catalog.domain.SearchText;
import de.dukkan.catalog.infrastructure.CatalogProductRepository;
import de.dukkan.catalog.infrastructure.CatalogProductRepository.SearchSource;
import de.dukkan.i18n.application.TextNormalizer;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds missing search columns ({@code catalog_product.search_text}, {@code
 * catalog_product_translation.search_name}) at startup, e.g. for SQL seed data, with the same
 * {@link TextNormalizer} that queries use. Products created through the API are indexed directly.
 */
@Component
@Order(0)
public class SearchTextIndexer implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(SearchTextIndexer.class);

  private final CatalogProductRepository products;

  public SearchTextIndexer(CatalogProductRepository products) {
    this.products = products;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    List<SearchSource> missing = products.findWithoutSearchText();
    for (SearchSource source : missing) {
      products.updateSearchText(source.id(), SearchText.build(source.names().values(), source.brand(), source.keywords()));
      source.names().forEach((locale, name) -> products.updateSearchName(source.id(), locale, TextNormalizer.normalize(name)));
    }
    if (!missing.isEmpty()) {
      LOG.info("Built search columns for {} catalog products", missing.size());
    }
  }
}
