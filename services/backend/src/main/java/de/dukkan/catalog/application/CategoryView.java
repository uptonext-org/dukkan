package de.dukkan.catalog.application;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Contract schema {@code Category}, localised. {@code productCount} is set on market-scoped lists. */
public record CategoryView(
    UUID id,
    @Nullable UUID parentId,
    String slug,
    String name,
    @Nullable String iconKey,
    @Nullable String imageUrl,
    int sortOrder,
    @Nullable Integer productCount) {}
