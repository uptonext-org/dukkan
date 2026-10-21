package de.dukkan.catalog.application;

import java.util.UUID;

/** Contract schema {@code PromotionBadge} with a localised label. */
public record PromotionBadge(UUID promotionId, String label, String type) {}
