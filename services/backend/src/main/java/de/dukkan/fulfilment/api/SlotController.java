package de.dukkan.fulfilment.api;

import de.dukkan.fulfilment.application.FulfilmentService;
import de.dukkan.shared.domain.FulfilmentType;
import de.dukkan.shared.security.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/v1")
@RestController
final class SlotController {

  private final FulfilmentService fulfilment;

  SlotController(FulfilmentService fulfilment) {
    this.fulfilment = fulfilment;
  }

  @GetMapping("/markets/{marketId}/slots")
  List<SlotResponse> slots(
      @CurrentSecurityContext SecurityContext security,
      @PathVariable UUID marketId,
      @RequestParam FulfilmentType type,
      @RequestParam(required = false) @Nullable UUID addressId) {
    return fulfilment.listSlots(marketId, type, CurrentUser.optionalId(security).orElse(null), addressId).stream()
        .map(SlotResponse::from)
        .toList();
  }
}
