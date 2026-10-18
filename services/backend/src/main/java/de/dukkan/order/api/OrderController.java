package de.dukkan.order.api;

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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/v1/orders")
@RestController
final class OrderController {

  private final OrderService orders;

  OrderController(OrderService orders) {
    this.orders = orders;
  }

  @GetMapping
  List<OrderResponse> list(@CurrentSecurityContext SecurityContext security) {
    return orders.list(CurrentUser.id(security)).stream().map(OrderResponse::from).toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  OrderResponse place(
      @CurrentSecurityContext SecurityContext security,
      @RequestHeader(name = "Idempotency-Key", required = false) @Nullable @Size(min = 1, max = 64)
          String idempotencyKey) {
    return OrderResponse.from(orders.placeOrder(CurrentUser.id(security), idempotencyKey));
  }

  @GetMapping("/{orderId}")
  OrderResponse get(@CurrentSecurityContext SecurityContext security, @PathVariable UUID orderId) {
    return OrderResponse.from(orders.get(CurrentUser.id(security), orderId));
  }

  @PostMapping("/{orderId}/cancel")
  OrderResponse cancel(@CurrentSecurityContext SecurityContext security, @PathVariable UUID orderId) {
    return OrderResponse.from(orders.cancel(CurrentUser.id(security), orderId));
  }
}
