package de.dukkan.catalog.application;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import de.dukkan.catalog.domain.Category;
import de.dukkan.catalog.domain.ComparisonPrice;
import de.dukkan.catalog.domain.Product;
import de.dukkan.catalog.domain.ProductPrice;
import de.dukkan.catalog.infrastructure.CategoryRepository;
import de.dukkan.catalog.infrastructure.ProductPriceRepository;
import de.dukkan.catalog.infrastructure.ProductRepository;
import de.dukkan.catalog.infrastructure.ProductSearchRepository;
import de.dukkan.promotion.application.PromotionRule;
import de.dukkan.promotion.application.PromotionService;
import de.dukkan.shared.error.NotFoundException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class DefaultCatalogService implements CatalogService {

  private final CategoryRepository categories;
  private final ProductRepository products;
  private final ProductPriceRepository prices;
  private final ProductSearchRepository search;
  private final PromotionService promotions;

  DefaultCatalogService(
      CategoryRepository categories,
      ProductRepository products,
      ProductPriceRepository prices,
      ProductSearchRepository search,
      PromotionService promotions) {
    this.categories = categories;
    this.products = products;
    this.prices = prices;
    this.search = search;
    this.promotions = promotions;
  }

  @Override
  public List<CategoryView> listCategories() {
    return categories.findAllByOrderBySortOrderAscNameAsc().stream().map(DefaultCatalogService::view).toList();
  }

  @Override
  public ProductPageView search(ProductQuery query) {
    Set<UUID> promoted = query.promotedOnly() ? promotions.promotedProductIds() : null;
    String q = query.q() == null || query.q().isBlank() ? null : query.q();
    ProductSearchRepository.Sort sort = ProductSearchRepository.Sort.valueOf(query.sort().toUpperCase(Locale.ROOT));
    ProductSearchRepository.IdPage ids =
        search.search(
            new ProductSearchRepository.Criteria(query.categoryId(), q, promoted, sort), query.page(), query.size());
    Map<UUID, ProductView> found = findProducts(ids.ids());
    List<ProductView> items = ids.ids().stream().map(found::get).filter(Objects::nonNull).toList();
    int totalPages = (int) ((ids.total() + query.size() - 1) / query.size());
    return new ProductPageView(items, query.page(), query.size(), ids.total(), totalPages);
  }

  @Override
  public ProductView getProduct(UUID productId) {
    ProductView product = findProducts(List.of(productId)).get(productId);
    if (product == null) {
      throw new NotFoundException("Product not found.");
    }
    return product;
  }

  @Override
  public List<String> suggest(String q, int limit) {
    return search.suggestNames(q, limit);
  }

  @Override
  public Map<UUID, ProductView> findProducts(Collection<UUID> productIds) {
    if (productIds.isEmpty()) {
      return Map.of();
    }
    Map<UUID, ProductPrice> current =
        prices.findCurrent(productIds).stream()
            .collect(toMap(ProductPrice::getProductId, identity()));
    Map<UUID, PromotionRule> rules = promotions.winningRules(productIds);
    Map<UUID, ProductView> result = new LinkedHashMap<>();
    for (Product product : products.findAllById(productIds)) {
      ProductPrice price = current.get(product.getId());
      if (price != null) {
        result.put(product.getId(), view(product, price, rules.get(product.getId())));
      }
    }
    return result;
  }

  private static ProductView view(Product p, ProductPrice price, PromotionRule rule) {
    return new ProductView(
        p.getId(),
        p.getSku(),
        p.getName(),
        p.getBrand(),
        p.getUnitSize(),
        p.getImageUrl(),
        price.getAmountCents(),
        ComparisonPrice.of(price.getAmountCents(), p.getNetQuantity(), p.getNetUnit()).orElse(null),
        rule,
        p.isAvailable(),
        p.getCategoryId(),
        p.getDescription(),
        p.getIngredients(),
        p.getAllergens(),
        p.getOrigin(),
        p.getMaxOrderQuantity());
  }

  private static CategoryView view(Category c) {
    return new CategoryView(c.getId(), c.getParentId(), c.getSlug(), c.getName(), c.getIconKey(), c.getSortOrder());
  }
}
