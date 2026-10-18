package de.dukkan.promotion.infrastructure;

import de.dukkan.promotion.domain.Promotion;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PromotionRepository extends JpaRepository<Promotion, UUID> {

  /** Promotions active at {@code now}, most recently created first. */
  @Query(
      """
      select distinct p from Promotion p left join fetch p.productIds
      where p.validFrom <= :now and p.validUntil > :now
      order by p.createdAt desc, p.id
      """)
  List<Promotion> findActive(Instant now);
}
