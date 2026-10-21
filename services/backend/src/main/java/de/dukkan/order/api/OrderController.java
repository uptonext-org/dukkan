package de.dukkan.order.api;

import de.dukkan.i18n.application.ContentLocale;
import de.dukkan.order.application.OrderService;
import de.dukkan.shared.security.CurrentUser;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/v1")
@RestController
final class OrderController {

  private final OrderService orders;
  private final OrderResponses responses;

  OrderController(OrderService orders, OrderResponses responses) {
    this.orders = orders;
    this.responses = responses;
  }

  @PostMapping("/markets/{marketId}/orders")
  @ResponseStatus(HttpStatus.CREATED)
  OrderResponse place(
      @CurrentSecurityContext SecurityContext security,
      @PathVariable UUID marketId,
      @RequestHeader(name = "Idempotency-Key", required = false) @Nullable @Size(min = 1, max = 64) String idempotencyKey,
      @RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    return responses.one(orders.placeOrder(CurrentUser.id(security), marketId, idempotencyKey, locale), locale);
  }

  @GetMapping("/orders")
  List<OrderResponse> list(@CurrentSecurityContext SecurityContext security, @RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    return responses.all(orders.list(CurrentUser.id(security), locale), locale);
  }

  @GetMapping("/orders/{orderId}")
  OrderResponse get(@CurrentSecurityContext SecurityContext security, @PathVariable UUID orderId, @RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    return responses.one(orders.get(CurrentUser.id(security), orderId, locale), locale);
  }

  @PostMapping("/orders/{orderId}/cancel")
  OrderResponse cancel(@CurrentSecurityContext SecurityContext security, @PathVariable UUID orderId, @RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    return responses.one(orders.cancel(CurrentUser.id(security), orderId, locale), locale);
  }
}
