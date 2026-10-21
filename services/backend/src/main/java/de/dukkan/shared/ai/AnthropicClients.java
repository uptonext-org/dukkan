package de.dukkan.shared.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import de.dukkan.shared.config.DukkanProperties;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

/**
 * The one Claude API client of the application ({@code dukkan.ai.anthropic}), shared by AI capture
 * and translation. Absent without an API key. Configured explicitly instead of {@code fromEnv()}, so
 * only Dukkan's own settings apply (not {@code ANTHROPIC_API_KEY} or {@code ANTHROPIC_BASE_URL}).
 */
@Component
public class AnthropicClients implements DisposableBean {

  private final DukkanProperties.Anthropic settings;
  private final @Nullable AnthropicClient client;

  public AnthropicClients(DukkanProperties properties) {
    this.settings = properties.ai().anthropic();
    this.client = create(settings);
  }

  public static @Nullable AnthropicClient create(DukkanProperties.Anthropic settings) {
    if (!settings.enabled()) {
      return null;
    }
    AnthropicOkHttpClient.Builder builder =
        AnthropicOkHttpClient.builder().apiKey(settings.apiKey()).timeout(settings.timeout()).maxRetries(1);
    if (settings.baseUrl() != null) {
      builder.baseUrl(settings.baseUrl());
    }
    return builder.build();
  }

  public Optional<AnthropicClient> client() {
    return Optional.ofNullable(client);
  }

  public DukkanProperties.Anthropic settings() {
    return settings;
  }

  @Override
  public void destroy() {
    if (client != null) {
      client.close();
    }
  }
}
