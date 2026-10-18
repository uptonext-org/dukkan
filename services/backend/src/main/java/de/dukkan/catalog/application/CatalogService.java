package de.dukkan.catalog.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Categories, product browsing/search and product lookups for other modules. */
public interface CatalogService {

  List<CategoryView> listCategories();

  ProductPageView search(ProductQuery query);

  /** Fails with {@code NOT_FOUND} if the product does not exist. */
  ProductView getProduct(UUID productId);

  List<String> suggest(String q, int limit);

  /** Current data for the given products; unknown ids are absent from the result. */
  Map<UUID, ProductView> findProducts(Collection<UUID> productIds);
}
