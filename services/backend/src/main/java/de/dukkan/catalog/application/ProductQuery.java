package de.dukkan.catalog.application;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Browse/search parameters. {@code sort} is one of relevance, price_asc, price_desc, name. */
public record ProductQuery(
    @Nullable UUID categoryId, @Nullable String q, boolean promotedOnly, String sort, int page, int size) {}
