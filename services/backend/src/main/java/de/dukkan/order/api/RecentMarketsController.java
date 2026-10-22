package de.dukkan.order.api;

import de.dukkan.i18n.application.ContentLocale;
import de.dukkan.order.application.RecentMarket;
import de.dukkan.order.application.RecentMarketsService;
import de.dukkan.shared.api.GeoPoint;
import de.dukkan.shared.error.InvalidRequestException;
import de.dukkan.shared.security.CurrentUser;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/v1")
@RestController
final class RecentMarketsController {

  private final RecentMarketsService recent;

  RecentMarketsController(RecentMarketsService recent) {
    this.recent = recent;
  }

  @GetMapping("/markets/recent")
  List<RecentMarket> recent(
      @CurrentSecurityContext SecurityContext security,
      @RequestParam(required = false) @Nullable @Size(max = RecentMarketsService.MAX_MARKETS) List<UUID> visited,
      @DecimalMax("90") @DecimalMin("-90") @RequestParam(required = false) @Nullable Double lat,
      @DecimalMax("180") @DecimalMin("-180") @RequestParam(required = false) @Nullable Double lng,
      @RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    if ((lat == null) != (lng == null)) {
      throw new InvalidRequestException(lat == null ? "lat" : "lng", "NotNull", "lat and lng must be given together");
    }
    return recent.recent(
        CurrentUser.optionalId(security).orElse(null),
        visited == null ? List.of() : visited,
        lat == null ? null : GeoPoint.of(lat, lng),
        locale);
  }
}
