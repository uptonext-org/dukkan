package de.dukkan.media.api;

import de.dukkan.shared.config.DukkanProperties;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Absolute public URLs of media keys: {@code dukkan.media.public-base-url} + "/" + key, or, when
 * that is not set, {@code <origin of the current request>/api/v1/media/<key>}. Behind a reverse
 * proxy set the base URL (or {@code server.forward-headers-strategy}), otherwise the URL names
 * the internal host.
 */
@Component
public class MediaUrls {

  static final String PATH = "/api/v1/media";

  private final @Nullable String publicBaseUrl;

  public MediaUrls(DukkanProperties properties) {
    String base = properties.media().publicBaseUrl();
    this.publicBaseUrl = base == null ? null : base.replaceAll("/+$", "");
  }

  /** Must be called while handling a request when no public base URL is configured. */
  public String urlFor(String key) {
    String base =
        publicBaseUrl != null ? publicBaseUrl : ServletUriComponentsBuilder.fromCurrentContextPath().path(PATH).toUriString();
    return base + "/" + key;
  }
}
