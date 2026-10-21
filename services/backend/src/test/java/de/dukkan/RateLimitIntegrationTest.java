package de.dukkan;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.dukkan.support.IntegrationTest;
import de.dukkan.support.TestImages;
import java.awt.Color;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.context.TestPropertySource;

/** Login and registration are rate limited per client IP, uploads per user. */
@TestPropertySource(
    properties = {
      "dukkan.security.rate-limit.enabled=true",
      "dukkan.security.rate-limit.logins-per-minute=3",
      "dukkan.security.rate-limit.registrations-per-hour=2",
      "dukkan.media.uploads-per-hour=2"
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

  @Test
  void uploadsAreLimitedPerUserAndHour() throws Exception {
    MvcResult registered =
        mvc.perform(
                json(
                    post("/api/v1/auth/register"),
                    toJson(
                        Map.of(
                            "email", uniqueEmail(),
                            "password", "correct-horse-42",
                            "firstName", "Upload",
                            "lastName", "Limit",
                            "accountType", "MERCHANT")))
                    .with(r -> ip(r, "192.0.2.50")))
            .andExpect(status().isCreated())
            .andReturn();
    String merchant = read(registered, "$.accessToken");
    UUID market = createMarket(merchant, "Limitmarkt");
    for (int i = 0; i < 2; i++) {
      mvc.perform(photo(merchant, market, i)).andExpect(status().isCreated());
    }
    mvc.perform(photo(merchant, market, 2))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    clock.advance(Duration.ofHours(1));
    mvc.perform(photo(merchant, market, 3)).andExpect(status().isCreated());
  }

  private static MockMultipartHttpServletRequestBuilder photo(String token, UUID market, int shade) {
    return multipart("/api/v1/merchant/markets/" + market + "/media")
        .file(new MockMultipartFile("file", "p.png", "image/png", TestImages.png(4, 4, new Color(shade, 100, 200))))
        .param("purpose", "PRODUCT")
        .header("Authorization", "Bearer " + token);
  }

  private String registration() {
    return toJson(Map.of("email", uniqueEmail(), "password", "correct-horse-42", "firstName", "Rate", "lastName", "Limit"));
  }

  private static MockHttpServletRequest ip(MockHttpServletRequest request, String address) {
    request.setRemoteAddr(address);
    return request;
  }
}
