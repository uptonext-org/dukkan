package de.dukkan.order.infrastructure;

import de.dukkan.order.domain.CustomerOrder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<CustomerOrder, UUID> {

  Optional<CustomerOrder> findByIdAndUserId(UUID id, UUID userId);

  Optional<CustomerOrder> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

  List<CustomerOrder> findByUserIdOrderByPlacedAtDescOrderNumberDesc(UUID userId);

  @Query(value = "SELECT nextval('order_number_seq')", nativeQuery = true)
  long nextOrderSequence();
}
