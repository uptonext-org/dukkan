package de.dukkan.catalog.infrastructure;

import de.dukkan.catalog.domain.Category;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

  List<Category> findAllByOrderBySortOrderAscNameAsc();
}
