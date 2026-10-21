package de.dukkan.market.application;

import de.dukkan.shared.api.CodeName;
import de.dukkan.shared.api.GeoPoint;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Contract schema {@code MarketSummary}, localised. */
public record MarketSummary(
    UUID id,
    String slug,
    String name,
    CodeName type,
    List<CodeName> cuisines,
    @Nullable String logoUrl,
    @Nullable String coverImageUrl,
    GeoPoint location,
    String addressLine,
    @Nullable Integer distanceMeters,
    @Nullable Boolean deliversToLocation,
    boolean openNow,
    @Nullable String todayHours,
    @Nullable OffsetDateTime nextOpeningAt,
    FulfilmentOptions fulfilment,
    int activePromotionCount,
    List<String> tags) {}
