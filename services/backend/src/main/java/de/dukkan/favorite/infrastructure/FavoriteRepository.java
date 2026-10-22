package de.dukkan.favorite.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class FavoriteRepository {

  private final JdbcClient jdbc;

  public FavoriteRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public void addMarket(UUID userId, UUID marketId, Instant now) {
    jdbc.sql("INSERT INTO favorite_market (user_id, market_id, created_at) VALUES (:u, :m, :now) ON CONFLICT DO NOTHING")
        .param("u", userId)
        .param("m", marketId)
        .param("now", Timestamp.from(now))
        .update();
  }

  public void removeMarket(UUID userId, UUID marketId) {
    jdbc.sql("DELETE FROM favorite_market WHERE user_id = :u AND market_id = :m").param("u", userId).param("m", marketId).update();
  }

  /** Favourite market ids, most recently added first. */
  public List<UUID> findMarketIds(UUID userId) {
    return jdbc.sql("SELECT market_id FROM favorite_market WHERE user_id = :u ORDER BY created_at DESC, market_id")
        .param("u", userId)
        .query(UUID.class)
        .list();
  }

  public void addProduct(UUID userId, UUID listingId, Instant now) {
    jdbc.sql(
            "INSERT INTO favorite_product (user_id, market_product_id, created_at) VALUES (:u, :p, :now) ON CONFLICT DO NOTHING")
        .param("u", userId)
        .param("p", listingId)
        .param("now", Timestamp.from(now))
        .update();
  }

  public void removeProduct(UUID userId, UUID listingId) {
    jdbc.sql("DELETE FROM favorite_product WHERE user_id = :u AND market_product_id = :p")
        .param("u", userId)
        .param("p", listingId)
        .update();
  }

  /** All favourite listing ids of the user, most recently added first. */
  public List<UUID> findProductIds(UUID userId) {
    return jdbc.sql("SELECT market_product_id FROM favorite_product WHERE user_id = :u ORDER BY created_at DESC")
        .param("u", userId)
        .query(UUID.class)
        .list();
  }

  public Set<UUID> findProductIdsAmong(UUID userId, Collection<UUID> listingIds) {
    if (listingIds.isEmpty()) {
      return Set.of();
    }
    return new HashSet<>(
        jdbc.sql("SELECT market_product_id FROM favorite_product WHERE user_id = :u AND market_product_id IN (:ids)")
            .param("u", userId)
            .param("ids", List.copyOf(Set.copyOf(listingIds)))
            .query(UUID.class)
            .list());
  }

  /** Favourite markets and products of a deleted account (B-51). */
  public void deleteAll(UUID userId) {
    jdbc.sql("DELETE FROM favorite_market WHERE user_id = :userId").param("userId", userId).update();
    jdbc.sql("DELETE FROM favorite_product WHERE user_id = :userId").param("userId", userId).update();
  }
}
