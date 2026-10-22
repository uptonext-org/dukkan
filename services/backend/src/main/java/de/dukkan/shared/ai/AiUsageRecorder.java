package de.dukkan.shared.ai;

import java.time.Duration;

/** Records calls to the language model (count, latency, tokens) per feature and outcome. */
public interface AiUsageRecorder {

  /** For tests and code paths without metrics. */
  AiUsageRecorder NONE = (feature, outcome, inputTokens, outputTokens, took) -> {};

  /**
   * Records one request; {@code feature} is {@code capture} or {@code translation}, {@code outcome}
   * one of {@code ok}, {@code declined}, {@code incomplete} or {@code error}.
   */
  void record(String feature, String outcome, long inputTokens, long outputTokens, Duration took);
}
