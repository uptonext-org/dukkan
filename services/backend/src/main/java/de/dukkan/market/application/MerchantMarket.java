package de.dukkan.market.application;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import java.util.Map;

/**
 * Contract schema {@code MerchantMarket}: MarketDetail plus the review status and the description
 * in every language ({@code descriptions}), so that editing the profile keeps all of them, and the
 * {@code review} checklist of the seller information.
 */
public record MerchantMarket(
    @JsonUnwrapped MarketDetail detail, String status, Map<String, String> descriptions, MarketReview review) {}
