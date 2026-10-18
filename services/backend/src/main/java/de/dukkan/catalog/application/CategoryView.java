package de.dukkan.catalog.application;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record CategoryView(UUID id, @Nullable UUID parentId, String slug, String name, @Nullable String iconKey, int sortOrder) {}
