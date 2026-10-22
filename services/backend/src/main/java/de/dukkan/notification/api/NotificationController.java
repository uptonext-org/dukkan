package de.dukkan.notification.api;

import de.dukkan.notification.application.NotificationService;
import de.dukkan.notification.application.NotificationService.InboxPage;
import de.dukkan.shared.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Push device registration and the notification inbox of the signed-in user. */
@RequestMapping("/api/v1/me")
@RestController
final class NotificationController {

  record DeviceRequest(@NotBlank @Size(max = 4096) String token, @Pattern(regexp = "IOS|ANDROID") @NotBlank String platform) {}

  record DeviceTokenRequest(@NotBlank @Size(max = 4096) String token) {}

  /** Contract schema {@code Notification}. */
  record NotificationResponse(
      UUID id, String kind, String title, String body, @Nullable UUID orderId, @Nullable UUID marketId, Instant createdAt, boolean read) {}

  /** Contract schema {@code NotificationPage}. */
  record NotificationPageResponse(List<NotificationResponse> items, int page, int size, long totalItems, int totalPages, long unreadCount) {}

  /** Contract schema {@code NotificationSettings}. */
  record NotificationSettings(@NotNull Boolean pushEnabled) {}

  private final NotificationService notifications;

  NotificationController(NotificationService notifications) {
    this.notifications = notifications;
  }

  @PutMapping("/devices")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void registerDevice(@CurrentSecurityContext SecurityContext security, @RequestBody @Valid DeviceRequest request) {
    notifications.registerDevice(CurrentUser.id(security), request.token(), request.platform());
  }

  @DeleteMapping("/devices")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void unregisterDevice(@CurrentSecurityContext SecurityContext security, @RequestBody @Valid DeviceTokenRequest request) {
    notifications.unregisterDevice(CurrentUser.id(security), request.token());
  }

  @GetMapping("/notifications")
  NotificationPageResponse inbox(
      @CurrentSecurityContext SecurityContext security,
      @Min(0) @RequestParam(defaultValue = "0") int page,
      @Max(100) @Min(1) @RequestParam(defaultValue = "30") int size) {
    InboxPage inbox = notifications.inbox(CurrentUser.id(security), page, size);
    List<NotificationResponse> items =
        inbox.items().stream()
            .map(n -> new NotificationResponse(n.id(), n.kind(), n.title(), n.body(), n.orderId(), n.marketId(), n.createdAt(), n.readAt() != null))
            .toList();
    int totalPages = (int) ((inbox.totalItems() + size - 1) / size);
    return new NotificationPageResponse(items, page, size, inbox.totalItems(), totalPages, inbox.unreadCount());
  }

  @GetMapping("/notification-settings")
  NotificationSettings settings(@CurrentSecurityContext SecurityContext security) {
    return new NotificationSettings(notifications.pushEnabled(CurrentUser.id(security)));
  }

  @PutMapping("/notification-settings")
  NotificationSettings updateSettings(@CurrentSecurityContext SecurityContext security, @RequestBody @Valid NotificationSettings request) {
    notifications.setPushEnabled(CurrentUser.id(security), request.pushEnabled());
    return new NotificationSettings(notifications.pushEnabled(CurrentUser.id(security)));
  }

  @PostMapping("/notifications/read")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void markAllRead(@CurrentSecurityContext SecurityContext security) {
    notifications.markAllRead(CurrentUser.id(security));
  }
}
