package de.dukkan.identity.infrastructure;

import de.dukkan.identity.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByEmail(String email);

  boolean existsByEmail(String email);

  /** Shared row lock until the transaction ends: blocks a deletion; true while the account is active. */
  @Query(value = "SELECT deleted_at IS NULL FROM app_user WHERE id = :id FOR SHARE", nativeQuery = true)
  Optional<Boolean> lockActive(@Param("id") UUID id);

  /**
   * Row lock for the deletion: waits for writers that hold {@link #lockActive}. NO KEY UPDATE, so
   * inserts that only reference the user (refresh tokens, carts) are not blocked; a plain FOR UPDATE
   * would deadlock with a token refresh that runs at the same time.
   */
  @Query(value = "SELECT CAST(id AS varchar) FROM app_user WHERE id = :id FOR NO KEY UPDATE", nativeQuery = true)
  Optional<String> lockForUpdate(@Param("id") UUID id);
}
