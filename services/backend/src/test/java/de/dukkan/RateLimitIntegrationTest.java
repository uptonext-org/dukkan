package de.dukkan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.support.IntegrationTest;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.TestPropertySource;

/** Login and registration are rate limited per client IP. */
@TestPropertySource(
    properties = {
      "dukkan.security.rate-limit.enabled=true",
      "dukkan.security.rate-limit.logins-per-minute=3",
      "dukkan.security.rate-limit.registrations-per-hour=2"
    })
class RateLimitIntegrationTest extends IntegrationTest {

  @Test
  void loginsAreLimitedPerMinuteAndIp() throws Exception {
    String body = toJson(Map.of("email", "nobody@example.com", "password", "wrong-password-123"));
    for (int i = 0; i < 3; i++) {
      mvc.perform(json(post("/api/v1/auth/login"), body).with(r -> ip(r, "203.0.113.7"))).andExpect(status().isUnauthorized());
    }
    mvc.perform(json(post("/api/v1/auth/login"), body).with(r -> ip(r, "203.0.113.7")))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    // Other clients are unaffected, and the window resets.
    mvc.perform(json(post("/api/v1/auth/login"), body).with(r -> ip(r, "203.0.113.8"))).andExpect(status().isUnauthorized());
    clock.advance(Duration.ofMinutes(1));
    mvc.perform(json(post("/api/v1/auth/login"), body).with(r -> ip(r, "203.0.113.7"))).andExpect(status().isUnauthorized());
  }

  @Test
  void registrationsAreLimitedPerHourAndIp() throws Exception {
    for (int i = 0; i < 2; i++) {
      mvc.perform(json(post("/api/v1/auth/register"), registration()).with(r -> ip(r, "198.51.100.4")))
          .andExpect(status().isCreated());
    }
    mvc.perform(json(post("/api/v1/auth/register"), registration()).with(r -> ip(r, "198.51.100.4")))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
  }

  private String registration() {
    return toJson(Map.of("email", uniqueEmail(), "password", "correct-horse-42", "firstName", "Rate", "lastName", "Limit"));
  }

  private static MockHttpServletRequest ip(MockHttpServletRequest request, String address) {
    request.setRemoteAddr(address);
    return request;
  }
}
