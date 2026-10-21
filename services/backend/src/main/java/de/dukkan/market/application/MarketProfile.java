package de.dukkan.market.application;

import de.dukkan.shared.api.GeoPoint;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** A merchant's market profile (contract schema {@code MarketProfileRequest}), already bean-validated. */
public record MarketProfile(
    String name,
    String typeCode,
    List<String> cuisineCodes,
    Map<String, String> description,
    String primaryLocale,
    String addressLine,
    GeoPoint location,
    @Nullable String phone,
    @Nullable String logoUrl,
    @Nullable String coverImageUrl,
    List<String> tags,
    FulfilmentOptions fulfilment,
    List<OpeningHours> openingHours,
    String legalName,
    @Nullable String registerInfo) {}
