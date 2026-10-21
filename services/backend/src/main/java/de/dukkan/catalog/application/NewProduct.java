package de.dukkan.catalog.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A market-private catalog product (contract schema {@code NewCatalogProduct}). */
public record NewProduct(
    Map<String, String> name,
    Map<String, String> description,
    Map<String, String> tagline,
    List<String> keywords,
    @Nullable String brand,
    String unitSize,
    @Nullable BigDecimal netQuantity,
    @Nullable String netUnit,
    UUID categoryId,
    List<String> cuisineCodes,
    boolean organic,
    List<String> allergens,
    @Nullable String imageUrl,
    @Nullable String barcode) {}
