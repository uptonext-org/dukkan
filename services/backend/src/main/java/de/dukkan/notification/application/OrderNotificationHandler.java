package de.dukkan.notification.application;

import de.dukkan.identity.application.IdentityService;
import de.dukkan.identity.application.UserView;
import de.dukkan.market.application.MarketService;
import de.dukkan.notification.application.NotificationTexts.Text;
import de.dukkan.notification.infrastructure.NotificationRepository;
import de.dukkan.order.application.OrderEvent;
import de.dukkan.shared.events.EventHandler;
import de.dukkan.shared.events.OutboxEvent;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns order events into inbox entries and pushes: merchants hear about new and cancelled orders,
 * customers about every merchant decision. Each recipient gets the text in their own language.
 * Pushes are best effort; the inbox entry is what counts.
 */
@Component
class OrderNotificationHandler implements EventHandler {

  private static final Logger LOG = LoggerFactory.getLogger(OrderNotificationHandler.class);

  private final NotificationRepository repository;
  private final PushGateway push;
  private final MarketService markets;
  private final IdentityService identity;
  private final JsonMapper json;
  private final Clock clock;

  OrderNotificationHandler(
      NotificationRepository repository, PushGateway push, MarketService markets, IdentityService identity, JsonMapper json, Clock clock) {
    this.repository = repository;
    this.push = push;
    this.markets = markets;
    this.identity = identity;
    this.json = json;
    this.clock = clock;
  }

  @Override
  public String name() {
    return "order-notifications";
  }

  @Override
  public Set<String> types() {
    return Set.of(OrderEvent.PLACED, OrderEvent.STATUS_CHANGED);
  }

  @Override
  public void handle(OutboxEvent event) {
    OrderEvent order = json.readValue(event.payload(), OrderEvent.class);
    for (UUID merchant : markets.ownerIds(order.marketId())) {
      notify(merchant, NotificationTexts.forMerchant(event.type(), order, locale(merchant)), order, "merchant");
    }
    notify(order.customerId(), NotificationTexts.forCustomer(event.type(), order, locale(order.customerId())), order, "customer");
  }

  private void notify(UUID userId, Optional<Text> text, OrderEvent order, String audience) {
    if (text.isEmpty() || !identity.holdActiveAccount(userId)) {
      // Deleted accounts get nothing, also from events that were still in the outbox. The row lock
      // keeps a deletion running right now from missing the notification written here.
      return;
    }
    Text t = text.get();
    repository.insert(UUID.randomUUID(), userId, t.kind(), t.title(), t.body(), order.orderId(), order.marketId(), clock.instant());
    if (!push.enabled() || !repository.pushEnabled(userId).orElse(true)) {
      // Push off (no FCM, or the user switched it off): the inbox entry is enough.
      return;
    }
    List<String> tokens = repository.deviceTokens(userId);
    if (tokens.isEmpty()) {
      return;
    }
    Map<String, String> data =
        Map.of("kind", t.kind(), "orderId", order.orderId().toString(), "marketId", order.marketId().toString(), "audience", audience);
    List<String> invalid = push.send(tokens, new PushGateway.PushMessage(t.title(), t.body(), data));
    if (!invalid.isEmpty()) {
      repository.deleteDevices(invalid);
      LOG.info("Removed {} expired device token(s) of user {}", invalid.size(), userId);
    }
  }

  private String locale(UUID userId) {
    UserView user = identity.getUser(userId);
    return user.preferredLocale() == null ? "de" : user.preferredLocale();
  }
}
