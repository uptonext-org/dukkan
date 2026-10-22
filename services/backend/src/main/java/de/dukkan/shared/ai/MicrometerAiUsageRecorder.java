package de.dukkan.shared.ai;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * {@code dukkan.ai.requests} (timer: count and latency) and {@code dukkan.ai.tokens} (counter, by
 * direction), tagged by feature and outcome, so cost and failure rate are visible per feature.
 */
@Component
class MicrometerAiUsageRecorder implements AiUsageRecorder {

  private final MeterRegistry registry;

  MicrometerAiUsageRecorder(MeterRegistry registry) {
    this.registry = registry;
  }

  @Override
  public void record(String feature, String outcome, long inputTokens, long outputTokens, Duration took) {
    Timer.builder("dukkan.ai.requests").tag("feature", feature).tag("outcome", outcome).register(registry).record(took);
    if (inputTokens > 0) {
      Counter.builder("dukkan.ai.tokens").tag("feature", feature).tag("direction", "input").register(registry).increment((double) inputTokens);
    }
    if (outputTokens > 0) {
      Counter.builder("dukkan.ai.tokens").tag("feature", feature).tag("direction", "output").register(registry).increment((double) outputTokens);
    }
  }
}
