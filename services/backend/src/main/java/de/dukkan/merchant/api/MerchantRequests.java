package de.dukkan.merchant.api;

import com.fasterxml.jackson.annotation.JsonSetter;
import de.dukkan.capture.application.ProductDraftInput;
import de.dukkan.catalog.application.ListingDraft;
import de.dukkan.catalog.application.ListingOverrides;
import de.dukkan.catalog.application.ListingUpdate;
import de.dukkan.catalog.application.NewProduct;
import de.dukkan.market.application.FulfilmentOptions;
import de.dukkan.market.application.MarketProfile;
import de.dukkan.market.application.OpeningHours;
import de.dukkan.promotion.application.PromotionDraft;
import de.dukkan.promotion.application.PromotionRule;
import de.dukkan.shared.api.GeoPoint;
import de.dukkan.shared.api.Money;
import de.dukkan.shared.error.InvalidRequestException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.hibernate.validator.constraints.URL;
import org.jspecify.annotations.Nullable;

/** Request bodies of the merchant endpoints (contract schemas of the same names). */
final class MerchantRequests {

  private MerchantRequests() {}

  record MarketProfileRequest(
      @NotBlank @Size(min = 2, max = 80) String name,
      @NotBlank String typeCode,
      @Nullable @Size(max = 5) List<@NotBlank String> cuisineCodes,
      @Nullable Map<String, @NotNull @Size(max = 2000) String> description,
      @NotBlank String primaryLocale,
      @NotBlank @Size(min = 5, max = 200) String addressLine,
      @NotNull @Valid GeoPoint location,
      @Nullable @Size(max = 30) String phone,
      @Nullable @URL String logoUrl,
      @Nullable @URL String coverImageUrl,
      @Nullable List<@Pattern(regexp = "ORGANIC|REGIONAL|HALAL|VEGAN_FRIENDLY") String> tags,
      @NotNull @Valid FulfilmentOptions fulfilment,
      @NotNull @Size(min = 1) List<@NotNull @Valid OpeningHours> openingHours,
      @NotBlank @Size(min = 2, max = 120) String legalName,
      @Nullable @Size(max = 120) String registerInfo) {

    MarketProfile toProfile() {
      return new MarketProfile(
          name,
          typeCode,
          cuisineCodes == null ? List.of() : cuisineCodes,
          description == null ? Map.of() : description,
          primaryLocale,
          addressLine,
          location,
          phone,
          logoUrl,
          coverImageUrl,
          tags == null ? List.of() : tags.stream().distinct().toList(),
          fulfilment,
          openingHours,
          legalName,
          registerInfo);
    }
  }

  record NewCatalogProductRequest(
      @NotNull @Size(min = 1) Map<String, @NotBlank @Size(max = 2000) String> name,
      @Nullable Map<String, @NotNull @Size(max = 2000) String> description,
      @Nullable Map<String, @NotNull @Size(max = 2000) String> tagline,
      @Nullable @Size(max = 30) List<@NotNull @Size(max = 60) String> keywords,
      @Nullable @Size(max = 80) String brand,
      @NotBlank @Size(min = 1, max = 40) String unitSize,
      @Nullable @Positive BigDecimal netQuantity,
      @Nullable @Pattern(regexp = "kg|l|piece") String netUnit,
      @NotNull UUID categoryId,
      @Nullable List<@NotBlank String> cuisineCodes,
      @Nullable Boolean organic,
      @Nullable @Size(max = 20) List<@NotBlank @Size(max = 60) String> allergens,
      @Nullable @URL String imageUrl,
      @Nullable @Size(max = 20) String barcode) {

    NewProduct toNewProduct() {
      return new NewProduct(
          name,
          description == null ? Map.of() : description,
          tagline == null ? Map.of() : tagline,
          keywords == null ? List.of() : keywords,
          brand,
          unitSize,
          netQuantity,
          netUnit,
          categoryId,
          cuisineCodes == null ? List.of() : cuisineCodes,
          Boolean.TRUE.equals(organic),
          allergens == null ? List.of() : allergens,
          imageUrl,
          barcode);
    }
  }

  record CreateListingRequest(
      @Nullable UUID catalogProductId,
      @Nullable @Valid NewCatalogProductRequest newProduct,
      @NotNull @Valid Money price,
      @Nullable Boolean available,
      @Nullable @Max(99) @Min(1) Integer maxOrderQuantity,
      @Nullable @Valid ListingOverrides overrides) {

    ListingDraft toDraft() {
      return new ListingDraft(
          catalogProductId,
          newProduct == null ? null : newProduct.toNewProduct(),
          euroCents(price, "price"),
          available == null || available,
          maxOrderQuantity,
          overrides == null ? ListingOverrides.NONE : overrides);
    }
  }

  /**
   * Partial listing update. A class rather than a record because {@code imageUrl} is tri-state:
   * absent (unchanged), {@code null} (remove) or a URL; the setter records that it was present.
   */
  static final class UpdateListingRequest {

    private @Nullable @Valid Money price;
    private @Nullable Boolean available;
    private @Nullable @Max(99) @Min(1) Integer maxOrderQuantity;
    private @Nullable @Size(max = 500) @URL String imageUrl;
    private boolean imageUrlPresent;
    private @Nullable @Valid ListingOverrides overrides;

    @JsonSetter("price")
    void setPrice(@Nullable Money price) {
      this.price = price;
    }

    @JsonSetter("available")
    void setAvailable(@Nullable Boolean available) {
      this.available = available;
    }

    @JsonSetter("maxOrderQuantity")
    void setMaxOrderQuantity(@Nullable Integer maxOrderQuantity) {
      this.maxOrderQuantity = maxOrderQuantity;
    }

    @JsonSetter("imageUrl")
    void setImageUrl(@Nullable String imageUrl) {
      this.imageUrl = imageUrl;
      this.imageUrlPresent = true;
    }

    @JsonSetter("overrides")
    void setOverrides(@Nullable ListingOverrides overrides) {
      this.overrides = overrides;
    }

    ListingUpdate toUpdate() {
      return new ListingUpdate(
          price == null ? null : euroCents(price, "price"), available, maxOrderQuantity, imageUrlPresent, imageUrl, overrides);
    }
  }

  /** A quantity price of a MULTI_BUY: {@code quantity} units together for {@code price}. */
  record TierRequest(@Max(99) @Min(2) int quantity, @NotNull @Valid Money price) {}

  /** The offer price of one product of a FIXED_PRICE promotion. */
  record OfferRequest(@NotNull UUID productId, @NotNull @Valid Money offerPrice) {}

  /**
   * A new promotion. MULTI_BUY takes {@code tiers} (or the older single {@code bundleQuantity} and
   * {@code bundlePrice}); FIXED_PRICE takes {@code offers}, whose products need not be repeated in
   * {@code productIds}. {@code weekdays} and the daily window narrow the validity.
   */
  record PromotionRequest(
      @NotNull @Size(min = 1) Map<String, @NotBlank @Size(max = 2000) String> title,
      @Nullable Map<String, @NotNull @Size(max = 2000) String> description,
      @NotNull @Pattern(regexp = "PERCENT_OFF|MULTI_BUY|FIXED_PRICE") String type,
      @Nullable @Max(90) @Min(1) Integer percentOff,
      @Nullable @Max(99) @Min(2) Integer bundleQuantity,
      @Nullable @Valid Money bundlePrice,
      @Nullable @Size(max = 5) List<@NotNull @Valid TierRequest> tiers,
      @Nullable @Size(max = 200) List<@NotNull @Valid OfferRequest> offers,
      @NotNull OffsetDateTime validFrom,
      @NotNull OffsetDateTime validUntil,
      @Nullable @Size(max = 7, min = 1) List<@NotNull @Max(7) @Min(1) Integer> weekdays,
      @Nullable LocalTime dailyFrom,
      @Nullable LocalTime dailyUntil,
      @Nullable @Size(max = 200) List<@NotNull UUID> productIds) {

    PromotionDraft toDraft() {
      List<PromotionRule.Tier> tierList = new ArrayList<>();
      if (tiers != null) {
        tiers.forEach(t -> tierList.add(new PromotionRule.Tier(t.quantity(), euroCents(t.price(), "tiers.price"))));
      } else if (bundleQuantity != null && bundlePrice != null) {
        tierList.add(new PromotionRule.Tier(bundleQuantity, euroCents(bundlePrice, "bundlePrice")));
      } else if (bundleQuantity != null || bundlePrice != null) {
        throw new InvalidRequestException("bundlePrice", "NotNull", "bundleQuantity and bundlePrice must be given together");
      }
      Map<UUID, Long> offerPrices = new LinkedHashMap<>();
      if (offers != null) {
        offers.forEach(o -> offerPrices.put(o.productId(), euroCents(o.offerPrice(), "offers.offerPrice")));
      }
      Set<UUID> ids = new LinkedHashSet<>(productIds == null ? List.of() : productIds);
      ids.addAll(offerPrices.keySet());
      if (ids.isEmpty()) {
        throw new InvalidRequestException("productIds", "NotEmpty", "at least one product");
      }
      return new PromotionDraft(
          title,
          description == null ? Map.of() : description,
          type,
          percentOff,
          tierList,
          offerPrices,
          validFrom.toInstant(),
          validUntil.toInstant(),
          weekdays == null ? null : weekdays.stream().distinct().sorted().toList(),
          dailyFrom,
          dailyUntil,
          List.copyOf(ids));
    }
  }

  record ProductDraftRequest(
      @Nullable @Size(max = 1000) String text,
      @Nullable @Pattern(regexp = "^[0-9a-f]{64}\\.(jpg|png|webp)$") String imageKey) {

    ProductDraftInput toInput() {
      return new ProductDraftInput(text, imageKey);
    }
  }

  record OrderStatusRequest(
      @NotNull
          @Pattern(regexp = "PLACED|ACCEPTED|REJECTED|READY_FOR_PICKUP|OUT_FOR_DELIVERY|COMPLETED|CANCELLED")
          String status) {}

  /** Units handed over per order line (missing items while packing); 0 = missing. */
  record ItemAdjustment(@Min(1) int lineNo, @Min(0) @Max(99) int fulfilledQuantity) {}

  record OrderItemsRequest(@NotEmpty @Size(max = 200) List<@Valid @NotNull ItemAdjustment> items) {

    Map<Integer, Integer> unitsByLine() {
      Map<Integer, Integer> units = new LinkedHashMap<>();
      for (ItemAdjustment item : items) {
        if (units.put(item.lineNo(), item.fulfilledQuantity()) != null) {
          throw new InvalidRequestException("items", "Unique", "each line at most once");
        }
      }
      return units;
    }
  }

  private static long euroCents(Money money, String field) {
    if (!money.isEuro()) {
      throw new InvalidRequestException(field + ".currency", "Currency", "must be EUR");
    }
    return money.amountCents();
  }
}
