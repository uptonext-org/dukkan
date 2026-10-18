package de.dukkan.delivery.infrastructure;

import de.dukkan.delivery.domain.Hub;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HubRepository extends JpaRepository<Hub, UUID> {}
