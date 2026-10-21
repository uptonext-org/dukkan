package de.dukkan.capture.application;

import de.dukkan.catalog.application.CatalogProduct;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Contract schema {@code ProductDraft}; {@code imageKey} is echoed so the API can add its URL. */
public record ProductDraftResult(
    DraftEngine engine,
    DraftProduct product,
    @Nullable Long priceCents,
    List<CatalogProduct> catalogMatches,
    List<DraftWarning> warnings,
    @Nullable String imageKey) {}
