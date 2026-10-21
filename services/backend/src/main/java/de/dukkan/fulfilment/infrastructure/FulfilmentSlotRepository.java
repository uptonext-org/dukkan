package de.dukkan.fulfilment.infrastructure;

import de.dukkan.fulfilment.domain.FulfilmentSlot;
import de.dukkan.shared.domain.FulfilmentType;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface FulfilmentSlotRepository extends JpaRepository<FulfilmentSlot, UUID> {

  List<FulfilmentSlot> findByMarketIdAndTypeAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAsc(
      UUID marketId, FulfilmentType type, Instant from, Instant to);

  /** Loads a slot with {@code SELECT ... FOR UPDATE} so that capacity changes are serialised. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from FulfilmentSlot s where s.id = :id")
  Optional<FulfilmentSlot> findByIdForUpdate(UUID id);

  /** Idempotent, race-free slot creation. */
  @Modifying
  @Query(
      value =
          """
          INSERT INTO fulfilment_slot (id, market_id, type, starts_at, ends_at, order_cutoff_at, capacity,
                                       used_capacity, version, created_at, updated_at)
          VALUES (:id, :marketId, :type, :startsAt, :endsAt, :cutoffAt, :capacity, 0, 0, now(), now())
          ON CONFLICT (market_id, type, starts_at) DO NOTHING
          """,
      nativeQuery = true)
  int insertIfAbsent(
      UUID id, UUID marketId, String type, Instant startsAt, Instant endsAt, Instant cutoffAt, int capacity);

  /**
   * Deletes slots that ended before {@code endedBefore} and are referenced by no order and no cart.
   * Slots of placed orders stay (orders snapshot their times but keep the FK for capacity release).
   */
  @Modifying
  @Query(
      value =
          """
          DELETE FROM fulfilment_slot s
           WHERE s.ends_at < :endedBefore
             AND NOT EXISTS (SELECT 1 FROM customer_order o WHERE o.slot_id = s.id)
             AND NOT EXISTS (SELECT 1 FROM cart c WHERE c.reserved_slot_id = s.id)
          """,
      nativeQuery = true)
  int deleteUnusedEndedBefore(Instant endedBefore);
}
