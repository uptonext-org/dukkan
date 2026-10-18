package de.dukkan.catalog.infrastructure;

import de.dukkan.catalog.domain.ProductPrice;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductPriceRepository extends JpaRepository<ProductPrice, UUID> {

  @Query("select p from ProductPrice p where p.productId in :productIds and p.validTo is null")
  List<ProductPrice> findCurrent(Collection<UUID> productIds);
}
