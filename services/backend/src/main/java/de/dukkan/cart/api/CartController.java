package de.dukkan.cart.api;

import de.dukkan.cart.application.CartService;
import de.dukkan.shared.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/v1/cart")
@RestController
final class CartController {

  /** Contract schema {@code SetQuantityRequest}. */
  record SetQuantityRequest(@Max(99) @Min(0) @NotNull Integer quantity) {}

  /** Contract schema {@code ReserveSlotRequest}. */
  record ReserveSlotRequest(@NotNull UUID slotId, @NotNull UUID addressId) {}

  private final CartService carts;

  CartController(CartService carts) {
    this.carts = carts;
  }

  @GetMapping
  CartResponse get(@CurrentSecurityContext SecurityContext security) {
    return CartResponse.from(carts.getCart(CurrentUser.id(security)));
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void clear(@CurrentSecurityContext SecurityContext security) {
    carts.clear(CurrentUser.id(security));
  }

  @PutMapping("/items/{productId}")
  CartResponse setQuantity(
      @CurrentSecurityContext SecurityContext security,
      @PathVariable UUID productId,
      @RequestBody @Valid SetQuantityRequest request) {
    return CartResponse.from(carts.setQuantity(CurrentUser.id(security), productId, request.quantity()));
  }

  @DeleteMapping("/items/{productId}")
  CartResponse removeItem(@CurrentSecurityContext SecurityContext security, @PathVariable UUID productId) {
    return CartResponse.from(carts.removeItem(CurrentUser.id(security), productId));
  }

  @PutMapping("/delivery")
  CartResponse reserve(@CurrentSecurityContext SecurityContext security, @RequestBody @Valid ReserveSlotRequest request) {
    return CartResponse.from(carts.reserveSlot(CurrentUser.id(security), request.slotId(), request.addressId()));
  }
}
