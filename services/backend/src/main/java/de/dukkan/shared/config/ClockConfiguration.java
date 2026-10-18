package de.dukkan.shared.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provides the application clock so that time-dependent rules are testable. */
@Configuration(proxyBeanMethods = false)
public class ClockConfiguration {

  /** The single place that reads the system clock; everything else injects this bean. */
  @Bean
  @SuppressWarnings("TimeZoneUsage")
  Clock clock() {
    return Clock.systemUTC();
  }
}
