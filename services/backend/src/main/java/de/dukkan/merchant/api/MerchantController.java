package de.dukkan.merchant.api;

import de.dukkan.capture.application.ProductDraftResult;
import de.dukkan.catalog.application.CatalogProduct;
import de.dukkan.catalog.application.Listing;
import de.dukkan.catalog.application.ListingImport;
import de.dukkan.i18n.application.ContentLocale;
import de.dukkan.market.application.MerchantMarket;
import de.dukkan.media.api.MediaUploadResponse;
import de.dukkan.media.api.MediaUrls;
import de.dukkan.media.application.MediaPurpose;
import de.dukkan.media.application.StoredMedia;
import de.dukkan.merchant.api.MerchantRequests.CreateListingRequest;
import de.dukkan.merchant.api.MerchantRequests.MarketProfileRequest;
import de.dukkan.merchant.api.MerchantRequests.OrderItemsRequest;
import de.dukkan.merchant.api.MerchantRequests.OrderStatusRequest;
import de.dukkan.merchant.api.MerchantRequests.ProductDraftRequest;
import de.dukkan.merchant.api.MerchantRequests.PromotionRequest;
import de.dukkan.merchant.api.MerchantRequests.UpdateListingRequest;
import de.dukkan.merchant.application.ListingCsv;
import de.dukkan.merchant.application.MerchantService;
import de.dukkan.order.api.OrderResponse;
import de.dukkan.order.application.MarketInsights;
import de.dukkan.order.api.OrderResponses;
import de.dukkan.promotion.application.PromotionView;
import de.dukkan.shared.api.PageResponse;
import de.dukkan.shared.config.DukkanProperties;
import de.dukkan.shared.error.InvalidRequestException;
import de.dukkan.shared.security.CurrentUser;
import jakarta.validation.Valid;
import java.io.IOException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Merchant mode ({@code /merchant/**}, role MERCHANT). Other merchants' resources answer 404. */
@RequestMapping("/api/v1/merchant")
@RestController
final class MerchantController {

  private final MerchantService merchant;
  private final OrderResponses orderResponses;
  private final MediaUrls mediaUrls;
  private final int defaultMaxOrderQuantity;

  MerchantController(MerchantService merchant, OrderResponses orderResponses, MediaUrls mediaUrls, DukkanProperties properties) {
    this.merchant = merchant;
    this.orderResponses = orderResponses;
    this.mediaUrls = mediaUrls;
    this.defaultMaxOrderQuantity = properties.catalog().defaultMaxOrderQuantity();
  }

  @GetMapping("/markets")
  List<MerchantMarket> myMarkets(@CurrentSecurityContext SecurityContext security, @RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    return merchant.myMarkets(CurrentUser.id(security), locale);
  }

  @PostMapping("/markets")
  @ResponseStatus(HttpStatus.CREATED)
  MerchantMarket createMarket(
      @CurrentSecurityContext SecurityContext security, @RequestBody @Valid MarketProfileRequest request, @RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    return merchant.createMarket(CurrentUser.id(security), request.toProfile(), locale);
  }

  @PutMapping("/markets/{marketId}")
  MerchantMarket updateMarket(
      @CurrentSecurityContext SecurityContext security,
      @PathVariable UUID marketId,
      @RequestBody @Valid MarketProfileRequest request,
      @RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    return merchant.updateMarket(CurrentUser.id(security), marketId, request.toProfile(), locale);
  }

  /** Multipart upload; the part's declared content type is ignored (magic bytes decide). */
  @PostMapping(path = "/markets/{marketId}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  ResponseEntity<MediaUploadResponse> uploadMedia(
      @CurrentSecurityContext SecurityContext security,
      @PathVariable UUID marketId,
      @RequestPart("file") MultipartFile file,
      @RequestParam MediaPurpose purpose) {
    StoredMedia stored = merchant.uploadMedia(CurrentUser.id(security), marketId, purpose, file.getSize(), file);
    String url = mediaUrls.urlFor(stored.key());
    return ResponseEntity.created(URI.create(url)).body(MediaUploadResponse.of(stored, url));
  }

  /** Bulk import from a CSV file (B-31); all or nothing, {@code dryRun} only checks. */
  @PostMapping(path = "/markets/{marketId}/listings/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  ListingImport.Result importListings(
      @CurrentSecurityContext SecurityContext security,
      @PathVariable UUID marketId,
      @RequestPart("file") MultipartFile file,
      @RequestParam(defaultValue = "false") boolean dryRun)
      throws IOException {
    if (file.getSize() > ListingCsv.MAX_BYTES) {
      throw new InvalidRequestException("file", "Size", "the file may have at most 1 MB");
    }
    return merchant.importListings(CurrentUser.id(security), marketId, file.getBytes(), dryRun);
  }

  /** AI product capture: a draft to review, nothing is saved. */
  @PostMapping("/markets/{marketId}/product-drafts")
  ProductDraftResponse draftProduct(
      @CurrentSecurityContext SecurityContext security,
      @PathVariable UUID marketId,
      @RequestBody @Valid ProductDraftRequest request,
      @RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    ProductDraftResult result = merchant.draftProduct(CurrentUser.id(security), marketId, request.toInput(), locale);
    return ProductDraftResponse.of(result, mediaUrls::urlFor);
  }

  @GetMapping("/catalog")
  PageResponse<CatalogProduct> catalog(
      @CurrentSecurityContext SecurityContext security,
      @RequestParam(required = false) @Nullable @Size(max = 100) String q,
      @RequestParam(required = false) @Nullable String cuisine,
      @RequestParam(required = false) @Nullable UUID categoryId,
      @RequestParam(required = false) @Nullable UUID marketId,
      @Min(0) @RequestParam(defaultValue = "0") int page,
      @Max(100) @Min(1) @RequestParam(defaultValue = "20") int size,
      @RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    return merchant.searchCatalog(CurrentUser.id(security), q, cuisine, categoryId, marketId, page, size, locale);
  }

  @GetMapping("/markets/{marketId}/listings")
  PageResponse<Listing> listings(
      @CurrentSecurityContext SecurityContext security,
      @PathVariable UUID marketId,
      @RequestParam(required = false) @Nullable @Size(max = 100) String q,
      @Min(0) @RequestParam(defaultValue = "0") int page,
      @Max(100) @Min(1) @RequestParam(defaultValue = "20") int size,
      @RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    return merchant.listings(CurrentUser.id(security), marketId, q, page, size, locale);
  }

  @PostMapping("/markets/{marketId}/listings")
  @ResponseStatus(HttpStatus.CREATED)
  Listing createListing(
      @CurrentSecurityContext SecurityContext security,
      @PathVariable UUID marketId,
      @RequestBody @Valid CreateListingRequest request,
      @RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    return merchant.createListing(CurrentUser.id(security), marketId, request.toDraft(defaultMaxOrderQuantity), locale);
  }

  @PutMapping("/markets/{marketId}/listings/{productId}")
  Listing updateListing(
      @CurrentSecurityContext SecurityContext security,
      @PathVariable UUID marketId,
      @PathVariable UUID productId,
      @RequestBody @Valid UpdateListingRequest request,
      @RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    return merchant.updateListing(CurrentUser.id(security), marketId, productId, request.toUpdate(), locale);
  }

  @DeleteMapping("/markets/{marketId}/listings/{productId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void deleteListing(
      @CurrentSecurityContext SecurityContext security, @PathVariable UUID marketId, @PathVariable UUID productId) {
    merchant.deleteListing(CurrentUser.id(security), marketId, productId);
  }

  @GetMapping("/markets/{marketId}/promotions")
  List<PromotionView> promotions(
      @CurrentSecurityContext SecurityContext security, @PathVariable UUID marketId, @RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    return merchant.promotions(CurrentUser.id(security), marketId, locale);
  }

  @PostMapping("/markets/{marketId}/promotions")
  @ResponseStatus(HttpStatus.CREATED)
  PromotionView createPromotion(
      @CurrentSecurityContext SecurityContext security,
      @PathVariable UUID marketId,
      @RequestBody @Valid PromotionRequest request,
      @RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    return merchant.createPromotion(CurrentUser.id(security), marketId, request.toDraft(), locale);
  }

  @DeleteMapping("/markets/{marketId}/promotions/{promotionId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void deletePromotion(
      @CurrentSecurityContext SecurityContext security, @PathVariable UUID marketId, @PathVariable UUID promotionId) {
    merchant.endPromotion(CurrentUser.id(security), marketId, promotionId);
  }

  @GetMapping("/markets/{marketId}/orders")
  List<OrderResponse> orders(
      @CurrentSecurityContext SecurityContext security,
      @PathVariable UUID marketId,
      @Pattern(regexp = "PLACED|ACCEPTED|REJECTED|READY_FOR_PICKUP|OUT_FOR_DELIVERY|COMPLETED|CANCELLED")
          @RequestParam(required = false)
          @Nullable
          String status,
      @RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    return orderResponses.all(merchant.orders(CurrentUser.id(security), marketId, status, locale), locale);
  }

  @GetMapping("/markets/{marketId}/insights")
  MarketInsightsResponse insights(
      @CurrentSecurityContext SecurityContext security,
      @PathVariable UUID marketId,
      @Min(1) @Max(366) @RequestParam(defaultValue = "30") int days,
      @RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    MarketInsights insights = merchant.insights(CurrentUser.id(security), marketId, days, locale);
    return MarketInsightsResponse.of(insights);
  }

  /** Missing items while packing: units handed over per line (0 = missing). */
  @PutMapping("/orders/{orderId}/items")
  OrderResponse adjustOrderItems(
      @CurrentSecurityContext SecurityContext security,
      @PathVariable UUID orderId,
      @RequestBody @Valid OrderItemsRequest request,
      @RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    return orderResponses.one(merchant.adjustOrderItems(CurrentUser.id(security), orderId, request.unitsByLine(), locale), locale);
  }

  @PostMapping("/orders/{orderId}/status")
  OrderResponse advanceOrder(
      @CurrentSecurityContext SecurityContext security,
      @PathVariable UUID orderId,
      @RequestBody @Valid OrderStatusRequest request,
      @RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    return orderResponses.one(merchant.advanceOrder(CurrentUser.id(security), orderId, request.status(), locale), locale);
  }
}
