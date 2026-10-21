package de.dukkan.catalog.application;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.dukkan.shared.api.Money;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Contract schema {@code ProductSummary}: a market's listing, localised and priced. */
public record ProductSummary(
    UUID id,
    UUID marketId,
    UUID catalogProductId,
    String name,
    @Nullable String brand,
    @Nullable String tagline,
    String unitSize,
    @Nullable String imageUrl,
    @Nullable String iconKey,
    Money price,
    @Nullable Money promotionalPrice,
    @Nullable UnitPrice comparisonPrice,
    @Nullable PromotionBadge promotion,
    boolean available,
    boolean organic,
    @JsonProperty("isNew") boolean isNew,
    int maxOrderQuantity,
    @Nullable Boolean favorite) {}
