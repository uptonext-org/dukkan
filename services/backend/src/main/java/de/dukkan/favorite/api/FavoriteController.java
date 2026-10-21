package de.dukkan.favorite.api;

import de.dukkan.favorite.application.FavoriteGroup;
import de.dukkan.favorite.application.FavoriteService;
import de.dukkan.i18n.application.ContentLocale;
import de.dukkan.market.application.MarketSummary;
import de.dukkan.shared.api.GeoPoint;
import de.dukkan.shared.error.InvalidRequestException;
import de.dukkan.shared.security.CurrentUser;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/v1")
@RestController
final class FavoriteController {

  private final FavoriteService favorites;

  FavoriteController(FavoriteService favorites) {
    this.favorites = favorites;
  }

  @GetMapping("/favorites/markets")
  List<MarketSummary> markets(
      @CurrentSecurityContext SecurityContext security,
      @DecimalMax("90") @DecimalMin("-90") @RequestParam(required = false) @Nullable Double lat,
      @DecimalMax("180") @DecimalMin("-180") @RequestParam(required = false) @Nullable Double lng,
      @RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    if ((lat == null) != (lng == null)) {
      throw new InvalidRequestException(lat == null ? "lat" : "lng", "NotNull", "lat and lng must be given together");
    }
    return favorites.markets(CurrentUser.id(security), lat == null ? null : GeoPoint.of(lat, lng), locale);
  }

  @PutMapping("/favorites/markets/{marketId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void addMarket(@CurrentSecurityContext SecurityContext security, @PathVariable UUID marketId) {
    favorites.addMarket(CurrentUser.id(security), marketId);
  }

  @DeleteMapping("/favorites/markets/{marketId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void removeMarket(@CurrentSecurityContext SecurityContext security, @PathVariable UUID marketId) {
    favorites.removeMarket(CurrentUser.id(security), marketId);
  }

  @GetMapping("/markets/{marketId}/favorites")
  List<FavoriteGroup> marketFavorites(
      @CurrentSecurityContext SecurityContext security, @PathVariable UUID marketId, @RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    return favorites.marketFavorites(CurrentUser.id(security), marketId, locale);
  }

  @PutMapping("/favorites/products/{productId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void addProduct(@CurrentSecurityContext SecurityContext security, @PathVariable UUID productId) {
    favorites.addProduct(CurrentUser.id(security), productId);
  }

  @DeleteMapping("/favorites/products/{productId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void removeProduct(@CurrentSecurityContext SecurityContext security, @PathVariable UUID productId) {
    favorites.removeProduct(CurrentUser.id(security), productId);
  }
}
