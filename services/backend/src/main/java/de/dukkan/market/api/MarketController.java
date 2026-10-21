package de.dukkan.market.api;

import de.dukkan.i18n.application.ContentLocale;
import de.dukkan.market.application.MarketDetail;
import de.dukkan.market.application.MarketInfo;
import de.dukkan.market.application.MarketQuery;
import de.dukkan.market.application.MarketService;
import de.dukkan.market.application.MarketSummary;
import de.dukkan.promotion.application.PromotionService;
import de.dukkan.promotion.application.PromotionView;
import de.dukkan.shared.api.CodeName;
import de.dukkan.shared.domain.FulfilmentType;
import de.dukkan.shared.api.GeoPoint;
import de.dukkan.shared.api.PageResponse;
import de.dukkan.shared.error.InvalidRequestException;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/v1")
@RestController
final class MarketController {

  private final MarketService markets;
  private final PromotionService promotions;

  MarketController(MarketService markets, PromotionService promotions) {
    this.markets = markets;
    this.promotions = promotions;
  }

  @GetMapping("/markets")
  PageResponse<MarketSummary> list(
      @DecimalMax("90") @DecimalMin("-90") @RequestParam double lat,
      @DecimalMax("180") @DecimalMin("-180") @RequestParam double lng,
      @DecimalMax("50") @DecimalMin("0.5") @RequestParam(defaultValue = "10") double radiusKm,
      @RequestParam(required = false) @Nullable List<String> types,
      @RequestParam(required = false) @Nullable List<String> cuisines,
      @RequestParam(required = false) @Nullable FulfilmentType fulfilment,
      @RequestParam(defaultValue = "false") boolean openNow,
      @RequestParam(defaultValue = "false") boolean hasOffers,
      @RequestParam(required = false) @Nullable @Size(max = 100) String q,
      @Pattern(
              regexp = "distance|name|minimum_order|delivery_fee",
              message = "must be one of distance, name, minimum_order, delivery_fee")
          @RequestParam(defaultValue = "distance")
          String sort,
      @Min(0) @RequestParam(defaultValue = "0") int page,
      @Max(100) @Min(1) @RequestParam(defaultValue = "20") int size,
      @RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    return markets.search(
        new MarketQuery(
            lat,
            lng,
            radiusKm,
            types == null ? List.of() : types,
            cuisines == null ? List.of() : cuisines,
            fulfilment,
            openNow,
            hasOffers,
            q,
            sort,
            page,
            size),
        locale);
  }

  @GetMapping("/markets/{marketId}")
  MarketDetail get(
      @PathVariable UUID marketId,
      @DecimalMax("90") @DecimalMin("-90") @RequestParam(required = false) @Nullable Double lat,
      @DecimalMax("180") @DecimalMin("-180") @RequestParam(required = false) @Nullable Double lng,
      @RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    if ((lat == null) != (lng == null)) {
      throw new InvalidRequestException(lat == null ? "lat" : "lng", "NotNull", "lat and lng must be given together");
    }
    return markets.detail(marketId, lat == null ? null : GeoPoint.of(lat, lng), locale);
  }

  @GetMapping("/markets/{marketId}/promotions")
  List<PromotionView> promotions(@PathVariable UUID marketId, @RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    MarketInfo market = markets.activeInfo(marketId);
    return promotions.listActive(marketId, locale, market.primaryLocale());
  }

  @GetMapping("/market-types")
  List<CodeName> marketTypes(@RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    return markets.marketTypes(locale);
  }

  @GetMapping("/cuisines")
  List<CodeName> cuisines(@RequestAttribute(ContentLocale.REQUEST_ATTRIBUTE) ContentLocale locale) {
    return markets.cuisines(locale);
  }
}
