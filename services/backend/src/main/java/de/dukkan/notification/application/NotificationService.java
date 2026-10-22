package de.dukkan.notification.application;

import de.dukkan.notification.infrastructure.NotificationRepository;
import de.dukkan.notification.infrastructure.NotificationRepository.NotificationRow;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Device registration for push and the user's notification inbox. */
@Service
@Transactional
public class NotificationService {

  /** A page of the inbox plus the number of unread entries (contract schema NotificationPage). */
  public record InboxPage(List<NotificationRow> items, int page, int size, long totalItems, long unreadCount) {}

  private final NotificationRepository repository;
  private final Clock clock;

  public NotificationService(NotificationRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  public void registerDevice(UUID userId, String token, String platform) {
    repository.upsertDevice(token, userId, platform, clock.instant());
  }

  public void unregisterDevice(UUID userId, String token) {
    repository.deleteDevice(token, userId);
  }

  @Transactional(readOnly = true)
  public InboxPage inbox(UUID userId, int page, int size) {
    return new InboxPage(repository.page(userId, page, size), page, size, repository.count(userId), repository.unreadCount(userId));
  }

  public void markAllRead(UUID userId) {
    repository.markAllRead(userId, clock.instant());
  }

  /** Whether push messages are on for the user (default on). */
  public boolean pushEnabled(UUID userId) {
    return repository.pushEnabled(userId).orElse(true);
  }

  public void setPushEnabled(UUID userId, boolean enabled) {
    repository.setPushEnabled(userId, enabled, clock.instant());
  }

  /** Everything notification-related of a deleted account. */
  public void deleteAll(UUID userId) {
    repository.deleteAllOf(userId);
  }

  /** One inbox entry for the data export (B-51). */
  public record ExportedNotification(String kind, String title, String body, java.time.Instant createdAt, boolean read) {}

  @Transactional(readOnly = true)
  public List<ExportedNotification> export(UUID userId) {
    return repository.page(userId, 0, 10_000).stream()
        .map(n -> new ExportedNotification(n.kind(), n.title(), n.body(), n.createdAt(), n.readAt() != null))
        .toList();
  }
}
