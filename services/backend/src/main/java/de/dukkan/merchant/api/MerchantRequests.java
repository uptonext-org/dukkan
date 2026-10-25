package de.dukkan.merchant.api;

import com.fasterxml.jackson.annotation.JsonSetter;
import de.dukkan.capture.application.ProductDraftInput;
import de.dukkan.catalog.application.ListingDraft;
import de.dukkan.catalog.application.ListingUpdate;
import de.dukkan.catalog.application.NewProduct;
import de.dukkan.market.application.FulfilmentOptions;
import de.dukkan.market.application.MarketProfile;
import de.dukkan.market.application.OpeningHours;
import de.dukkan.promotion.application.PromotionDraft;
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
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
      @Nullable @Max(99) @Min(1) Integer maxOrderQuantity) {

    ListingDraft toDraft() {
      return new ListingDraft(
          catalogProductId,
          newProduct == null ? null : newProduct.toNewProduct(),
          euroCents(price, "price"),
          available == null || available,
          maxOrderQuantity);
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

    ListingUpdate toUpdate() {
      return new ListingUpdate(
          price == null ? null : euroCents(price, "price"), available, maxOrderQuantity, imageUrlPresent, imageUrl);
    }
  }

  record PromotionRequest(
      @NotNull @Size(min = 1) Map<String, @NotBlank @Size(max = 2000) String> title,
      @Nullable Map<String, @NotNull @Size(max = 2000) String> description,
      @NotNull @Pattern(regexp = "PERCENT_OFF|MULTI_BUY") String type,
      @Nullable @Max(90) @Min(1) Integer percentOff,
      @Nullable @Max(20) @Min(2) Integer bundleQuantity,
      @Nullable @Valid Money bundlePrice,
      @NotNull OffsetDateTime validFrom,
      @NotNull OffsetDateTime validUntil,
      @NotNull @Size(max = 200, min = 1) List<@NotNull UUID> productIds) {

    PromotionDraft toDraft() {
      return new PromotionDraft(
          title,
          description == null ? Map.of() : description,
          type,
          percentOff,
          bundleQuantity,
          bundlePrice == null ? null : euroCents(bundlePrice, "bundlePrice"),
          validFrom.toInstant(),
          validUntil.toInstant(),
          productIds.stream().distinct().toList());
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
