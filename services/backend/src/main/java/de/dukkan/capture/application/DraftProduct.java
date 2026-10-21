package de.dukkan.capture.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Contract schema {@code ProductDraftProduct} without {@code imageUrl} (added by the API layer). */
public record DraftProduct(
    Map<String, String> name,
    Map<String, String> description,
    List<String> keywords,
    @Nullable String brand,
    @Nullable String unitSize,
    @Nullable BigDecimal netQuantity,
    @Nullable String netUnit,
    @Nullable UUID categoryId,
    List<String> cuisineCodes,
    boolean organic,
    List<String> allergens,
    @Nullable String barcode) {}
