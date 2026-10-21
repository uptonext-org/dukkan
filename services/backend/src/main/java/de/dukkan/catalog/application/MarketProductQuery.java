package de.dukkan.catalog.application;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Browse/search parameters of one market; {@code sort} is relevance, price_asc, price_desc or name. */
public record MarketProductQuery(
    @Nullable String q,
    @Nullable UUID categoryId,
    boolean promotedOnly,
    boolean newOnly,
    boolean organicOnly,
    String sort,
    int page,
    int size) {}
