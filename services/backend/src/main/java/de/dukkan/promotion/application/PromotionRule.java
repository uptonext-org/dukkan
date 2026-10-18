package de.dukkan.promotion.application;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.UUID;

/** The pricing-relevant part of an active promotion. */
public sealed interface PromotionRule {

  UUID promotionId();

  /** Short badge text, e.g. {@code -20%} or {@code 3 for 3.00 €}. */
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

  /** Each complete group of {@code bundleQuantity} units costs {@code bundlePriceCents}. */
  record MultiBuy(UUID promotionId, int bundleQuantity, long bundlePriceCents) implements PromotionRule {
    public MultiBuy {
      if (bundleQuantity < 2) {
        throw new IllegalArgumentException("bundleQuantity must be >= 2");
      }
      if (bundlePriceCents < 0) {
        throw new IllegalArgumentException("bundlePriceCents must be >= 0");
      }
    }

    @Override
    public String label() {
      NumberFormat format = NumberFormat.getNumberInstance(Locale.ROOT);
      format.setMinimumFractionDigits(2);
      format.setMaximumFractionDigits(2);
      return bundleQuantity + " for " + format.format(bundlePriceCents / 100.0) + " €";
    }

    @Override
    public String type() {
      return "MULTI_BUY";
    }
  }
}
