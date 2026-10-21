package de.dukkan.market.application;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Contract schema {@code Banner}, localised. */
public record Banner(UUID id, @Nullable String overline, String title, @Nullable String imageUrl, String tint, Target target) {

  /** Where tapping the banner leads. */
  public record Target(String type, @Nullable UUID id) {}
}
