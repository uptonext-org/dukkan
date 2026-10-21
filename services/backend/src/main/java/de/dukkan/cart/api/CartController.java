package de.dukkan.cart.api;

import de.dukkan.cart.application.CartService;
import de.dukkan.cart.application.CartView;
import de.dukkan.i18n.application.ContentLocale;
import de.dukkan.market.application.MarketService;
import de.dukkan.market.application.MarketSummary;
import de.dukkan.shared.domain.FulfilmentType;
import de.dukkan.shared.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/v1")
@RestController
final class CartController {

  /** Contract schema {@code SetQuantityRequest}. */
  record SetQuantityRequest(@Max(99) @Min(0) @NotNull Integer quantity) {}

  /** Contract schema {@code ReserveFulfilmentRequest}. */
  record ReserveFulfilmentRequest(@NotNull FulfilmentType type, @NotNull UUID slotId, @Nullable UUID addressId) {}

  private final CartService carts;
  private final MarketService markets;

  CartController(CartService carts, MarketService markets) {
    this.carts = carts;
    this.markets = markets;
  }

  @GetMapping("/carts")
  List<CartResponse.Summary> list(@CurrentSecurityContext SecurityContext security, @RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    List<CartView> all = carts.listCarts(CurrentUser.id(security), locale);
    Map<UUID, MarketSummary> summaries = markets.summaries(all.stream().map(CartView::marketId).toList(), null, locale);
    return all.stream().map(c -> CartResponse.Summary.from(c, summaries.get(c.marketId()))).toList();
  }

  @GetMapping("/markets/{marketId}/cart")
  CartResponse get(@CurrentSecurityContext SecurityContext security, @PathVariable UUID marketId, @RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    return response(carts.getCart(CurrentUser.id(security), marketId, locale), locale);
  }

  @DeleteMapping("/markets/{marketId}/cart")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void clear(@CurrentSecurityContext SecurityContext security, @PathVariable UUID marketId) {
    carts.clear(CurrentUser.id(security), marketId);
  }

  @PutMapping("/markets/{marketId}/cart/items/{productId}")
  CartResponse setQuantity(
      @CurrentSecurityContext SecurityContext security,
      @PathVariable UUID marketId,
      @PathVariable UUID productId,
      @RequestBody @Valid SetQuantityRequest request,
      @RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    return response(carts.setQuantity(CurrentUser.id(security), marketId, productId, request.quantity(), locale), locale);
  }

  @DeleteMapping("/markets/{marketId}/cart/items/{productId}")
  CartResponse removeItem(
      @CurrentSecurityContext SecurityContext security,
      @PathVariable UUID marketId,
      @PathVariable UUID productId,
      @RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    return response(carts.removeItem(CurrentUser.id(security), marketId, productId, locale), locale);
  }

  @PutMapping("/markets/{marketId}/cart/fulfilment")
  CartResponse reserve(
      @CurrentSecurityContext SecurityContext security,
      @PathVariable UUID marketId,
      @RequestBody @Valid ReserveFulfilmentRequest request,
      @RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    return response(
        carts.reserve(CurrentUser.id(security), marketId, request.type(), request.slotId(), request.addressId(), locale),
        locale);
  }

  private CartResponse response(CartView cart, @RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    return CartResponse.from(cart, markets.summary(cart.marketId(), locale));
  }
}
