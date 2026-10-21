package de.dukkan.catalog.application;

import de.dukkan.market.application.Banner;
import de.dukkan.market.application.MarketSummary;
import java.util.List;

/** Contract schema {@code MarketHome}. */
public record MarketHome(MarketSummary market, List<Banner> banners, List<HomeSection> sections) {}
