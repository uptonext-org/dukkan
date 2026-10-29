package de.dukkan.order.infrastructure;

import de.dukkan.order.domain.CustomerOrder;
import de.dukkan.order.domain.OrderStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<CustomerOrder, UUID> {

  Optional<CustomerOrder> findByIdAndUserId(UUID id, UUID userId);

  Optional<CustomerOrder> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

  /**
   * Serialises placements with the same customer and Idempotency-Key until the transaction ends,
   * so a concurrent repeat finds the first order instead of failing on the unique key.
   */
  @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(CAST(:userId AS text) || '|' || :idempotencyKey, 0))", nativeQuery = true)
  Integer lockIdempotencyKey(UUID userId, String idempotencyKey);

  List<CustomerOrder> findByUserIdOrderByPlacedAtDescOrderNumberDesc(UUID userId);

  boolean existsByUserIdAndStatusIn(UUID userId, Collection<OrderStatus> statuses);

  /**
   * Account deletion (B-51): the order stays for the market's records, without the customer's
   * name, street, house number, delivery notes and coordinates. Postal code and city remain.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          "UPDATE customer_order SET customer_name = 'Gelöschtes Konto', address_label = NULL,"
              + " address_street = CASE WHEN address_street IS NULL THEN NULL ELSE '-' END, address_house_number = NULL,"
              + " address_addition = NULL, address_delivery_instructions = NULL, address_lat = NULL, address_lng = NULL,"
              + " address_id = NULL, idempotency_key = NULL WHERE user_id = :userId",
      nativeQuery = true)
  int anonymizeCustomer(UUID userId);

  /**
   * Data minimisation for finished orders placed before {@code cutoff}: the delivery address and
   * notes go (as on account deletion); name, items, amounts, postal code and city stay, so the
   * customer and the market keep the order history.
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      value =
          "UPDATE customer_order SET address_label = NULL,"
              + " address_street = CASE WHEN address_street IS NULL THEN NULL ELSE '-' END, address_house_number = NULL,"
              + " address_addition = NULL, address_delivery_instructions = NULL, address_lat = NULL, address_lng = NULL,"
              + " address_id = NULL, idempotency_key = NULL"
              + " WHERE placed_at < :cutoff AND status IN ('COMPLETED', 'CANCELLED', 'REJECTED')"
              + " AND (address_house_number IS NOT NULL OR address_lat IS NOT NULL OR address_delivery_instructions IS NOT NULL"
              + " OR idempotency_key IS NOT NULL)",
      nativeQuery = true)
  int minimiseFinishedBefore(Instant cutoff);

  List<CustomerOrder> findByMarketIdOrderByPlacedAtDescOrderNumberDesc(UUID marketId);

  List<CustomerOrder> findByMarketIdAndStatusOrderByPlacedAtDescOrderNumberDesc(UUID marketId, OrderStatus status);

  @Query(value = "SELECT nextval('order_number_seq')", nativeQuery = true)
  long nextOrderSequence();
}
