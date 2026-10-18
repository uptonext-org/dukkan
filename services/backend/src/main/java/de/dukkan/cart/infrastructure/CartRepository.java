package de.dukkan.cart.infrastructure;

import de.dukkan.cart.domain.Cart;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CartRepository extends JpaRepository<Cart, UUID> {

  Optional<Cart> findByUserId(UUID userId);

  /**
   * Loads the cart for modification. The version is always incremented on commit, so concurrent
   * writers (including item-only changes) fail with an optimistic locking conflict.
   */
  @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
  @Query("select c from Cart c where c.userId = :userId")
  Optional<Cart> findByUserIdForUpdate(UUID userId);

  /** Creates the user's cart if it does not exist yet; safe under concurrent first access. */
  @Modifying
  @Query(
      value =
          """
          INSERT INTO cart (id, user_id, version, created_at, updated_at)
          VALUES (:id, :userId, 0, now(), now())
          ON CONFLICT (user_id) DO NOTHING
          """,
      nativeQuery = true)
  int insertIfAbsent(UUID id, UUID userId);
}
