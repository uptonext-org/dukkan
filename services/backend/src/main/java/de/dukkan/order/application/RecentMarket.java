package de.dukkan.order.application;

import de.dukkan.catalog.application.ProductSummary;
import de.dukkan.market.application.MarketSummary;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A market the user ordered from or visited recently. Without orders (only visited, or a guest)
 * the order fields are null and {@code recentProducts} is empty.
 */
public record RecentMarket(
    MarketSummary market,
    @Nullable Instant lastOrderAt,
    @Nullable UUID lastOrderId,
    int orderCount,
    List<ProductSummary> recentProducts) {}
