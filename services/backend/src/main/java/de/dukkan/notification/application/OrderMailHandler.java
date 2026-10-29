package de.dukkan.notification.application;

import de.dukkan.i18n.application.ContentLocale;
import de.dukkan.identity.application.IdentityService;
import de.dukkan.identity.application.UserView;
import de.dukkan.legal.application.PlatformSettings;
import de.dukkan.market.application.MarketService;
import de.dukkan.notification.application.OrderMails.Kind;
import de.dukkan.order.application.OrderEvent;
import de.dukkan.order.application.OrderService;
import de.dukkan.shared.events.EventHandler;
import de.dukkan.shared.events.OutboxEvent;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * E-mails the customer about their order: placed (the legally required confirmation), changed after
 * missing items, declined by the market, cancelled. Its own event handler, so a mail server that is
 * down only retries the mails, not the inbox entries.
 */
@Component
class OrderMailHandler implements EventHandler {

  private static final Logger LOG = LoggerFactory.getLogger(OrderMailHandler.class);

  private final Mailer mailer;
  private final OrderService orders;
  private final MarketService markets;
  private final IdentityService identity;
  private final PlatformSettings platform;
  private final JsonMapper json;

  OrderMailHandler(
      Mailer mailer,
      OrderService orders,
      MarketService markets,
      IdentityService identity,
      PlatformSettings platform,
      JsonMapper json) {
    this.mailer = mailer;
    this.orders = orders;
    this.markets = markets;
    this.identity = identity;
    this.platform = platform;
    this.json = json;
  }

  @Override
  public String name() {
    return "order-mails";
  }

  @Override
  public Set<String> types() {
    return Set.of(OrderEvent.PLACED, OrderEvent.STATUS_CHANGED, OrderEvent.ITEMS_ADJUSTED);
  }

  static Optional<Kind> kind(String type, OrderEvent order) {
    return switch (type) {
      case OrderEvent.PLACED -> Optional.of(Kind.PLACED);
      case OrderEvent.ITEMS_ADJUSTED -> Optional.of(Kind.ADJUSTED);
      case OrderEvent.STATUS_CHANGED ->
          switch (order.status()) {
            case "REJECTED" -> Optional.of(Kind.REJECTED);
            case "CANCELLED" -> Optional.of(Kind.CANCELLED);
            default -> Optional.empty();
          };
      default -> Optional.empty();
    };
  }

  @Override
  public void handle(OutboxEvent event) {
    OrderEvent order = json.readValue(event.payload(), OrderEvent.class);
    Optional<Kind> kind = kind(event.type(), order);
    if (kind.isEmpty() || identity.isDeleted(order.customerId())) {
      return;
    }
    if (!mailer.enabled()) {
      LOG.debug("No SMTP server: order mail for {} skipped", order.orderNumber());
      return;
    }
    UserView customer = identity.getUser(order.customerId());
    String locale = customer.preferredLocale() == null ? ContentLocale.DEFAULT : customer.preferredLocale();
    ContentLocale content = ContentLocale.of(locale);
    OrderMails.Mail mail =
        OrderMails.build(
            new OrderMails.Input(
                kind.get(),
                locale,
                customer.firstName(),
                orders.get(order.customerId(), order.orderId(), content),
                // Any status: a market suspended after the order still sends the rejection or cancellation mail.
                markets.detailAnyStatus(order.marketId(), content),
                platform,
                platform.webUrl()));
    mailer.send(customer.email(), mail.subject(), mail.body());
  }
}
