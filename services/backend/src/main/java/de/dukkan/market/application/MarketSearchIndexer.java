package de.dukkan.market.application;

import de.dukkan.i18n.application.TextNormalizer;
import de.dukkan.market.infrastructure.MarketRepository;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fills {@code market.search_name} at startup for rows without it (SQL seed data, markets from
 * before V10) with the same {@link TextNormalizer} the name filter uses (B-41).
 */
@Component
@Order(0)
class MarketSearchIndexer implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(MarketSearchIndexer.class);

  private final MarketRepository markets;

  MarketSearchIndexer(MarketRepository markets) {
    this.markets = markets;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    Map<UUID, String> missing = markets.findWithoutSearchName();
    missing.forEach((id, name) -> markets.updateSearchName(id, TextNormalizer.normalize(name)));
    if (!missing.isEmpty()) {
      LOG.info("Built search names for {} markets", missing.size());
    }
  }
}
