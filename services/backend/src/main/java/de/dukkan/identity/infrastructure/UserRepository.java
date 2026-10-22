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

  /** Exclusive row lock for the deletion: waits for writers that hold {@link #lockActive}. */
  @Query(value = "SELECT CAST(id AS varchar) FROM app_user WHERE id = :id FOR UPDATE", nativeQuery = true)
  Optional<String> lockForUpdate(@Param("id") UUID id);
}
