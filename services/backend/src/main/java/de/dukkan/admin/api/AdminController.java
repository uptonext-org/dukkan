package de.dukkan.admin.api;

import de.dukkan.i18n.application.ContentLocale;
import de.dukkan.market.application.MarketService;
import de.dukkan.market.application.MerchantMarket;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Platform operators (role ADMIN): review, approve and suspend markets. */
@RequestMapping("/api/v1/admin")
@RestController
final class AdminController {

  private static final String STATUSES = "PENDING_REVIEW|ACTIVE|SUSPENDED";

  private final MarketService markets;

  AdminController(MarketService markets) {
    this.markets = markets;
  }

  @GetMapping("/markets")
  List<MerchantMarket> markets(
      @RequestParam(required = false) @Nullable @Pattern(regexp = STATUSES, message = "must be one of " + STATUSES) String status,
      @RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    return markets.listForAdmin(status, locale);
  }

  @PostMapping("/markets/{marketId}/status")
  MerchantMarket setStatus(
      @PathVariable UUID marketId,
      @RequestBody @Valid MarketStatusRequest request,
      @RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    return markets.setStatus(marketId, request.status(), locale);
  }

  record MarketStatusRequest(@NotNull @Pattern(regexp = STATUSES, message = "must be one of " + STATUSES) String status) {}
}
