package de.dukkan.market.application;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

/** Contract schema {@code MerchantMarket}: MarketDetail plus the review status. */
public record MerchantMarket(@JsonUnwrapped MarketDetail detail, String status) {}
