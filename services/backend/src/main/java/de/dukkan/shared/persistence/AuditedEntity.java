package de.dukkan.shared.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.domain.Persistable;

/**
 * Base class for entities with application-assigned UUIDs and {@code created_at}/{@code
 * updated_at} audit columns. Implements {@link Persistable} so that saving a new entity issues a
 * plain INSERT instead of a merge.
 */
@MappedSuperclass
public abstract class AuditedEntity implements Persistable<UUID> {

  @Column(name = "created_at", nullable = false, updatable = false)
  @CreationTimestamp
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  @UpdateTimestamp
  private Instant updatedAt;

  @Transient private boolean persisted;

  @PostLoad
  @PostPersist
  void markPersisted() {
    persisted = true;
  }

  @Override
  public boolean isNew() {
    return !persisted;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
