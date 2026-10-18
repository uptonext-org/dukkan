package de.dukkan.address.infrastructure;

import de.dukkan.address.domain.Address;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AddressRepository extends JpaRepository<Address, UUID> {

  Optional<Address> findByIdAndUserId(UUID id, UUID userId);

  List<Address> findByUserIdOrderByDefaultAddressDescCreatedAtAsc(UUID userId);

  boolean existsByUserId(UUID userId);

  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query("update Address a set a.defaultAddress = false where a.userId = :userId and a.defaultAddress = true")
  int clearDefault(UUID userId);
}
