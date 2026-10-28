package de.dukkan.admin.api;

import de.dukkan.catalog.application.CatalogImport;
import de.dukkan.catalog.application.CatalogModerationService;
import de.dukkan.catalog.application.CatalogService;
import de.dukkan.feature.application.Feature;
import de.dukkan.feature.application.FeatureService;
import de.dukkan.i18n.application.ContentLocale;
import de.dukkan.i18n.application.LocaleService;
import de.dukkan.i18n.application.SupportedLocale;
import de.dukkan.market.application.MarketService;
import de.dukkan.market.application.MerchantMarket;
import de.dukkan.merchant.application.ListingCsv;
import de.dukkan.shared.api.PageResponse;
import de.dukkan.shared.error.InvalidRequestException;
import de.dukkan.shared.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Platform operators (role ADMIN): review markets, and products merchants add to the catalog. */
@RequestMapping("/api/v1/admin")
@RestController
final class AdminController {

  private static final String STATUSES = "PENDING_REVIEW|ACTIVE|SUSPENDED";

  private final MarketService markets;
  private final CatalogModerationService moderation;
  private final FeatureService features;
  private final CatalogService catalog;
  private final LocaleService locales;

  AdminController(MarketService markets, CatalogModerationService moderation, FeatureService features, CatalogService catalog, LocaleService locales) {
    this.markets = markets;
    this.moderation = moderation;
    this.features = features;
    this.catalog = catalog;
    this.locales = locales;
  }

  record FeatureRequest(@NotNull Boolean enabled) {}

  /** The switches as set (a sub-feature keeps its value while the shopping list is off). */
  @GetMapping("/features")
  Map<Feature, Boolean> features() {
    return features.switches();
  }

  @PutMapping("/features/{feature}")
  Map<Feature, Boolean> setFeature(
      @CurrentSecurityContext SecurityContext security, @PathVariable Feature feature, @RequestBody @Valid FeatureRequest request) {
    return features.set(feature, request.enabled(), CurrentUser.id(security));
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

  // ------------------------------------------------------------------ catalog review (B-36a)

  @GetMapping("/catalog/products")
  PageResponse<CatalogModerationService.Item> catalogQueue(
      @RequestParam(defaultValue = "PENDING") @Pattern(regexp = "PENDING|KEPT_PRIVATE|PROMOTED", message = "must be one of PENDING, KEPT_PRIVATE, PROMOTED") String status,
      @Min(0) @RequestParam(defaultValue = "0") int page,
      @Max(100) @Min(1) @RequestParam(defaultValue = "20") int size,
      @RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    return moderation.queue(status, page, size, locale);
  }

  @PutMapping("/catalog/products/{productId}/texts")
  CatalogModerationService.Item updateTexts(
      @PathVariable UUID productId,
      @RequestBody @Valid ProductTextsRequest request,
      @RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    return moderation.updateTexts(productId, request.names(), request.descriptions() == null ? Map.of() : request.descriptions(), locale);
  }

  @PostMapping("/catalog/products/{productId}/review")
  CatalogModerationService.Item review(
      @PathVariable UUID productId,
      @RequestBody @Valid ReviewRequest request,
      @RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    return moderation.decide(productId, request.decision(), locale);
  }

  /**
   * Shared catalog import from a CSV file (the listing import's columns without prices); dry run
   * by default. See {@link CatalogService#importShared}.
   */
  @PostMapping(path = "/catalog/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  CatalogImport.Result importCatalog(@RequestPart("file") MultipartFile file, @RequestParam(defaultValue = "true") boolean dryRun) throws IOException {
    if (file.getSize() > ListingCsv.MAX_BYTES) {
      throw new InvalidRequestException("file", "Size", "the file may have at most 1 MB");
    }
    List<String> codes = locales.enabled().stream().map(SupportedLocale::code).toList();
    return catalog.importShared(ListingCsv.parseCatalog(file.getBytes(), codes), dryRun);
  }

  record ProductTextsRequest(
      @NotNull @Size(max = 10) Map<@Pattern(regexp = "^[a-z]{2}$") String, @NotNull @Size(max = 150) String> names,
      @Nullable @Size(max = 10) Map<@Pattern(regexp = "^[a-z]{2}$") String, @NotNull @Size(max = 2000) String> descriptions) {}

  record ReviewRequest(@NotNull CatalogModerationService.Decision decision) {}

  record MarketStatusRequest(@NotNull @Pattern(regexp = STATUSES, message = "must be one of " + STATUSES) String status) {}
}
