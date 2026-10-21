package de.dukkan.catalog.application;

import java.util.Map;
import java.util.UUID;

/** Names and descriptions of a market's own product per locale (for machine translation, B-38). */
public record PrivateProductTexts(UUID catalogProductId, UUID marketId, String primaryLocale, Map<String, String> names, Map<String, String> descriptions) {}
