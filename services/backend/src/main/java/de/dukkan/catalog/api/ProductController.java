package de.dukkan.catalog.api;

import de.dukkan.catalog.application.CatalogService;
import de.dukkan.catalog.application.CategoryView;
import de.dukkan.catalog.application.MarketOffer;
import de.dukkan.catalog.application.ProductDetail;
import de.dukkan.i18n.application.ContentLocale;
import de.dukkan.shared.api.GeoPoint;
import de.dukkan.shared.api.PageResponse;
import de.dukkan.shared.security.CurrentUser;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/v1")
@RestController
final class ProductController {

  private final CatalogService catalog;

  ProductController(CatalogService catalog) {
    this.catalog = catalog;
  }

  @GetMapping("/categories")
  List<CategoryView> categories(@RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    return catalog.allCategories(locale);
  }

  @GetMapping("/products/{productId}")
  ProductDetail product(
      @CurrentSecurityContext SecurityContext security, @PathVariable UUID productId, @RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    return catalog.product(productId, locale, CurrentUser.optionalId(security).orElse(null));
  }

  @GetMapping("/search/products")
  PageResponse<MarketOffer> searchNearby(
      @CurrentSecurityContext SecurityContext security,
      @RequestParam @Size(min = 2, max = 100) String q,
      @DecimalMax("90") @DecimalMin("-90") @RequestParam double lat,
      @DecimalMax("180") @DecimalMin("-180") @RequestParam double lng,
      @DecimalMax("50") @DecimalMin("0.5") @RequestParam(defaultValue = "10") double radiusKm,
      @Pattern(regexp = "relevance|price_asc|distance", message = "must be one of relevance, price_asc, distance")
          @RequestParam(defaultValue = "relevance")
          String sort,
      @Min(0) @RequestParam(defaultValue = "0") int page,
      @Max(100) @Min(1) @RequestParam(defaultValue = "20") int size,
      @RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    return catalog.searchNearby(
        q, GeoPoint.of(lat, lng), radiusKm, sort, page, size, locale, CurrentUser.optionalId(security).orElse(null));
  }
}
