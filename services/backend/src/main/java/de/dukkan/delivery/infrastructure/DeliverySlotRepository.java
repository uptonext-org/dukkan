package de.dukkan.delivery.infrastructure;

import de.dukkan.delivery.domain.DeliverySlot;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface DeliverySlotRepository extends JpaRepository<DeliverySlot, UUID> {

  List<DeliverySlot> findByHubIdAndStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAsc(
      UUID hubId, Instant from, Instant to);

  /** Loads a slot with {@code SELECT ... FOR UPDATE} so that capacity changes are serialised. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from DeliverySlot s where s.id = :id")
  Optional<DeliverySlot> findByIdForUpdate(UUID id);

  /** Idempotent, race-free slot creation. */
  @Modifying
  @Query(
      value =
          """
          INSERT INTO delivery_slot (id, hub_id, starts_at, ends_at, order_cutoff_at, fee_cents, capacity,
                                     used_capacity, version, created_at, updated_at)
          VALUES (:id, :hubId, :startsAt, :endsAt, :cutoffAt, :feeCents, :capacity, 0, 0, now(), now())
          ON CONFLICT (hub_id, starts_at) DO NOTHING
          """,
      nativeQuery = true)
  int insertIfAbsent(
      UUID id, UUID hubId, Instant startsAt, Instant endsAt, Instant cutoffAt, long feeCents, int capacity);
}
