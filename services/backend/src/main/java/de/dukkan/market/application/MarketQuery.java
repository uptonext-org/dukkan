package de.dukkan.market.application;

import de.dukkan.shared.domain.FulfilmentType;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Parameters of {@code GET /markets}; {@code sort} is distance, name, minimum_order or delivery_fee. */
public record MarketQuery(
    double lat,
    double lng,
    double radiusKm,
    List<String> types,
    List<String> cuisines,
    @Nullable FulfilmentType fulfilment,
    boolean openNow,
    boolean hasOffers,
    @Nullable String q,
    String sort,
    int page,
    int size) {}
