package de.dukkan.promotion.application;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Input for creating a promotion; listing ids (and offer prices) must already be validated against
 * the market. {@code tiers} are the quantity prices of a MULTI_BUY, {@code offerPrices} the price
 * per listing of a FIXED_PRICE. {@code weekdays} (ISO, 1 = Monday) and the daily window in Berlin
 * time narrow {@code validFrom}–{@code validUntil}; {@code null} means every day / all day.
 */
public record PromotionDraft(
    Map<String, String> title,
    Map<String, String> description,
    String type,
    @Nullable Integer percentOff,
    List<PromotionRule.Tier> tiers,
    Map<UUID, Long> offerPrices,
    Instant validFrom,
    Instant validUntil,
    @Nullable List<Integer> weekdays,
    @Nullable LocalTime dailyFrom,
    @Nullable LocalTime dailyUntil,
    List<UUID> listingIds) {}
