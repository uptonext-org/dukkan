package de.dukkan.catalog.infrastructure;

import de.dukkan.catalog.domain.Product;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, UUID> {}
