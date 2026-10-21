package de.dukkan.catalog.application;

import de.dukkan.shared.api.CodeName;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Contract schema {@code CatalogProduct} (shared catalog entry as seen by a merchant). */
public record CatalogProduct(
    UUID id,
    String name,
    @Nullable String brand,
    String unitSize,
    @Nullable String imageUrl,
    @Nullable String iconKey,
    UUID categoryId,
    List<CodeName> cuisines,
    boolean organic,
    @Nullable String barcode,
    @Nullable Boolean alreadyListed) {}
