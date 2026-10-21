package de.dukkan.catalog.api;

import de.dukkan.catalog.application.CatalogService;
import de.dukkan.catalog.application.CategoryView;
import de.dukkan.catalog.application.MarketHome;
import de.dukkan.catalog.application.MarketHomeService;
import de.dukkan.catalog.application.MarketProductQuery;
import de.dukkan.catalog.application.ProductSummary;
import de.dukkan.i18n.application.ContentLocale;
import de.dukkan.shared.api.PageResponse;
import de.dukkan.shared.security.CurrentUser;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/v1/markets/{marketId}")
@RestController
final class MarketCatalogController {

  private final CatalogService catalog;
  private final MarketHomeService home;

  MarketCatalogController(CatalogService catalog, MarketHomeService home) {
    this.catalog = catalog;
    this.home = home;
  }

  @GetMapping("/home")
  MarketHome home(
      @CurrentSecurityContext SecurityContext security, @PathVariable UUID marketId, @RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    return home.home(marketId, locale, CurrentUser.optionalId(security).orElse(null));
  }

  @GetMapping("/categories")
  List<CategoryView> categories(@PathVariable UUID marketId, @RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    return catalog.marketCategories(marketId, locale);
  }

  @GetMapping("/products")
  PageResponse<ProductSummary> products(
      @CurrentSecurityContext SecurityContext security,
      @PathVariable UUID marketId,
      @RequestParam(required = false) @Nullable @Size(min = 1, max = 100) String q,
      @RequestParam(required = false) @Nullable UUID categoryId,
      @RequestParam(defaultValue = "false") boolean promotedOnly,
      @RequestParam(defaultValue = "false") boolean newOnly,
      @RequestParam(defaultValue = "false") boolean organicOnly,
      @Pattern(regexp = "relevance|price_asc|price_desc|name", message = "must be one of relevance, price_asc, price_desc, name")
          @RequestParam(defaultValue = "relevance")
          String sort,
      @Min(0) @RequestParam(defaultValue = "0") int page,
      @Max(100) @Min(1) @RequestParam(defaultValue = "20") int size,
      @RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    return catalog.searchMarket(
        marketId,
        new MarketProductQuery(q, categoryId, promotedOnly, newOnly, organicOnly, sort, page, size),
        locale,
        CurrentUser.optionalId(security).orElse(null));
  }

  @GetMapping("/search/suggestions")
  List<String> suggestions(
      @PathVariable UUID marketId,
      @RequestParam @Size(min = 2, max = 100) String q,
      @Max(10) @Min(1) @RequestParam(defaultValue = "5") int limit,
      @RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    return catalog.suggestions(marketId, q, limit, locale);
  }
}
