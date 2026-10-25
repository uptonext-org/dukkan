package de.dukkan.promotion.application;

import java.text.NumberFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/** The pricing-relevant part of an active promotion for one listing. */
public sealed interface PromotionRule {

  UUID promotionId();

  /** Short badge text, e.g. {@code -20%}, {@code 3 for 5.00 €} or {@code 2.00 €}. */
  String label();

  String type();

  /** Each unit is discounted by {@code round_half_up(unitPrice * percentOff / 100)}. */
  record PercentOff(UUID promotionId, int percentOff) implements PromotionRule {
    public PercentOff {
      if (percentOff < 1 || percentOff > 100) {
        throw new IllegalArgumentException("percentOff must be within 1..100");
      }
    }

    @Override
    public String label() {
      return "-" + percentOff + "%";
    }

    @Override
    public String type() {
      return "PERCENT_OFF";
    }
  }

  /** {@code quantity} units together cost {@code priceCents}. */
  record Tier(int quantity, long priceCents) {
    public Tier {
      if (quantity < 2) {
        throw new IllegalArgumentException("quantity must be >= 2");
      }
      if (priceCents < 0) {
        throw new IllegalArgumentException("priceCents must be >= 0");
      }
    }
  }

  /**
   * Quantity prices, e.g. 3 for 5 € and 7 for 10 €. A line costs the cheapest combination of tiers
   * and single units ({@link PricingEngine}). Tiers are kept sorted by quantity.
   */
  record MultiBuy(UUID promotionId, List<Tier> tiers) implements PromotionRule {
    public MultiBuy {
      if (tiers.isEmpty()) {
        throw new IllegalArgumentException("at least one tier");
      }
      tiers = tiers.stream().sorted(Comparator.comparingInt(Tier::quantity)).toList();
    }

    /** A single tier ("3 for 5 €"). */
    public MultiBuy(UUID promotionId, int bundleQuantity, long bundlePriceCents) {
      this(promotionId, List.of(new Tier(bundleQuantity, bundlePriceCents)));
    }

    /** The smallest tier's quantity. */
    public int bundleQuantity() {
      return tiers.getFirst().quantity();
    }

    /** The smallest tier's price. */
    public long bundlePriceCents() {
      return tiers.getFirst().priceCents();
    }

    @Override
    public String label() {
      return tiers.stream().map(t -> t.quantity() + " for " + rootEuro(t.priceCents())).collect(Collectors.joining(" · "));
    }

    @Override
    public String type() {
      return "MULTI_BUY";
    }
  }

  /** Each unit costs {@code offerPriceCents} (never more than the regular price). */
  record FixedPrice(UUID promotionId, long offerPriceCents) implements PromotionRule {
    public FixedPrice {
      if (offerPriceCents < 0) {
        throw new IllegalArgumentException("offerPriceCents must be >= 0");
      }
    }

    @Override
    public String label() {
      return rootEuro(offerPriceCents);
    }

    @Override
    public String type() {
      return "FIXED_PRICE";
    }
  }

  private static String rootEuro(long cents) {
    NumberFormat format = NumberFormat.getNumberInstance(Locale.ROOT);
    format.setMinimumFractionDigits(2);
    format.setMaximumFractionDigits(2);
    return format.format(cents / 100.0) + " €";
  }
}
