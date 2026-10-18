package de.dukkan.delivery.api;

import de.dukkan.delivery.application.DeliveryService;
import de.dukkan.delivery.application.HubView;
import de.dukkan.shared.security.CurrentUser;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/v1")
@RestController
final class DeliveryController {

  /** Contract schema {@code DeliveryAreaCheck}. */
  record DeliveryAreaCheckResponse(String postalCode, boolean serviceable, @Nullable String hubName) {}

  private final DeliveryService delivery;

  DeliveryController(DeliveryService delivery) {
    this.delivery = delivery;
  }

  @GetMapping("/delivery-areas/check")
  DeliveryAreaCheckResponse check(
      @Pattern(regexp = "^[0-9]{5}$", message = "must be a 5-digit German postal code") @RequestParam
          String postalCode) {
    Optional<HubView> hub = delivery.findHubForPostalCode(postalCode);
    return new DeliveryAreaCheckResponse(postalCode, hub.isPresent(), hub.map(HubView::name).orElse(null));
  }

  @GetMapping("/delivery-slots")
  List<DeliverySlotResponse> slots(@CurrentSecurityContext SecurityContext security, @RequestParam UUID addressId) {
    return delivery.listSlotsForAddress(CurrentUser.id(security), addressId).stream()
        .map(DeliverySlotResponse::from)
        .toList();
  }
}
