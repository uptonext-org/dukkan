package de.dukkan.market.application;

import java.util.UUID;

/** An active market and its great-circle distance from a search location. */
public record MarketDistance(UUID marketId, double distanceMeters) {}
