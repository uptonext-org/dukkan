package de.dukkan.catalog.api;

import de.dukkan.catalog.api.CatalogDtos.CategoryResponse;
import de.dukkan.catalog.api.CatalogDtos.ProductDetail;
import de.dukkan.catalog.api.CatalogDtos.ProductPage;
import de.dukkan.catalog.application.CatalogService;
import de.dukkan.catalog.application.ProductQuery;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/v1")
@RestController
final class CatalogController {

  private final CatalogService catalog;

  CatalogController(CatalogService catalog) {
    this.catalog = catalog;
  }

  @GetMapping("/categories")
  List<CategoryResponse> categories() {
    return catalog.listCategories().stream().map(CategoryResponse::from).toList();
  }

  @GetMapping("/products")
  ProductPage products(
      @RequestParam(required = false) @Nullable UUID categoryId,
      @RequestParam(required = false) @Nullable @Size(min = 1, max = 100) String q,
      @RequestParam(defaultValue = "false") boolean promotedOnly,
      @Pattern(regexp = "relevance|price_asc|price_desc|name", message = "must be one of relevance, price_asc, price_desc, name")
          @RequestParam(defaultValue = "relevance")
          String sort,
      @Min(0) @RequestParam(defaultValue = "0") int page,
      @Max(100) @Min(1) @RequestParam(defaultValue = "20") int size) {
    return ProductPage.from(catalog.search(new ProductQuery(categoryId, q, promotedOnly, sort, page, size)));
  }

  @GetMapping("/products/{productId}")
  ProductDetail product(@PathVariable UUID productId) {
    return ProductDetail.from(catalog.getProduct(productId));
  }

  @GetMapping("/search/suggestions")
  List<String> suggestions(
      @RequestParam @Size(min = 2, max = 100) String q,
      @Max(10) @Min(1) @RequestParam(defaultValue = "5") int limit) {
    return catalog.suggest(q, limit);
  }
}
