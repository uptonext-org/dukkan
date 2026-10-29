package de.dukkan.catalog.application;

/**
 * A listing with every detail of its product, for the merchant's edit form: the product's own
 * values in every language (for a shared catalog product they are read-only; the market changes
 * them with {@link ListingOverrides}).
 */
public record ListingDetails(Listing listing, NewProduct product) {}
