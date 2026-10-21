package de.dukkan.catalog.application;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Contract schema {@code HomeSection}. */
public record HomeSection(
    String key, String title, @Nullable String subtitle, @Nullable UUID categoryId, List<ProductSummary> items) {}
