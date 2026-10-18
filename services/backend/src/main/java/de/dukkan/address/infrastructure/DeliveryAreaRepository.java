package de.dukkan.address.infrastructure;

import de.dukkan.address.domain.DeliveryArea;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryAreaRepository extends JpaRepository<DeliveryArea, String> {

  List<DeliveryArea> findByPostalCodeIn(Collection<String> postalCodes);
}
