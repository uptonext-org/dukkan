package de.dukkan.catalog.application;

import de.dukkan.market.application.MarketSummary;

/** Contract schema {@code MarketOffer}. */
public record MarketOffer(ProductSummary product, MarketSummary market) {}
