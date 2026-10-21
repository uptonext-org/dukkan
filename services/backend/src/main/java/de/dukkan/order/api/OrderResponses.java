package de.dukkan.order.api;

import de.dukkan.i18n.application.ContentLocale;
import de.dukkan.market.application.MarketService;
import de.dukkan.market.application.MarketSummary;
import de.dukkan.order.application.OrderView;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Maps order views to responses with localised market summaries (batched per request). */
@Component
public final class OrderResponses {

  private final MarketService markets;

  public OrderResponses(MarketService markets) {
    this.markets = markets;
  }

  public OrderResponse one(OrderView order, ContentLocale locale) {
    return OrderResponse.from(order, markets.summary(order.marketId(), locale));
  }

  public List<OrderResponse> all(List<OrderView> orders, ContentLocale locale) {
    Map<UUID, MarketSummary> summaries = markets.summaries(orders.stream().map(OrderView::marketId).toList(), null, locale);
    return orders.stream().map(o -> OrderResponse.from(o, summaries.get(o.marketId()))).toList();
  }
}
