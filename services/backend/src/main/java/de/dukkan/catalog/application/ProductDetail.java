package de.dukkan.catalog.application;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import de.dukkan.market.application.MarketSummary;
import de.dukkan.shared.api.CodeName;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Contract schema {@code ProductDetail}. */
public record ProductDetail(
    @JsonUnwrapped ProductSummary summary,
    UUID categoryId,
    @Nullable String description,
    @Nullable String ingredients,
    List<String> allergens,
    @Nullable String origin,
    List<CodeName> cuisines,
    @Nullable String barcode,
    MarketSummary market) {}
