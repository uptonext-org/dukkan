package de.dukkan;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import de.dukkan.support.IntegrationTest;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MvcResult;

/** Regression tests for the findings of the security and correctness review (2026-10-21). */
class ReviewFindingsRegressionTest extends IntegrationTest {

  @Value("${dukkan.security.jwt.secret}")
  private String jwtSecret;

  @Value("${dukkan.security.jwt.issuer}")
  private String jwtIssuer;

  /** M1: an empty cart cannot hold slot capacity, and emptying a cart gives its capacity back. */
  @Test
  void emptyCartsCannotBlockSlotCapacity() throws Exception {
    UUID market = marketId(BAKERY);
    String token = registerUser();
    String slot = availableSlot(slots(null, market, "PICKUP", null), 1);
    int before = remaining(market, slot);

    MvcResult empty = reserve(token, market, "PICKUP", slot, null);
    assertThat(empty.getResponse().getStatus()).isEqualTo(422);
    assertThat((String) read(empty, "$.code")).isEqualTo("CART_EMPTY");
    assertThat(remaining(market, slot)).isEqualTo(before);

    UUID listing = availableListing(BAKERY, 100);
    setQuantity(token, market, listing, 1);
    assertThat(reserve(token, market, "PICKUP", slot, null).getResponse().getStatus()).isEqualTo(200);
    assertThat(remaining(market, slot)).isEqualTo(before - 1);

    MvcResult emptied = setQuantity(token, market, listing, 0);
    assertThat(object(emptied, "$.fulfilment")).isNull();
    assertThat(remaining(market, slot)).isEqualTo(before);
  }

  /** M2: bcrypt's 72-byte limit gives a 400 at registration and 401 at login, never a 500. */
  @Test
  void overlongPasswordsAreRejectedInsteadOfFailing() throws Exception {
    String arabic = "كلمةالمرورطويلةجداكلمةالمرورطويلةجدا1234"; // 40 characters, 76 bytes
    assertThat(arabic.getBytes(UTF_8).length).isGreaterThan(72);
    Map<String, Object> body = new HashMap<>();
    body.put("email", uniqueEmail());
    body.put("password", arabic);
    body.put("firstName", "Lina");
    body.put("lastName", "Test");
    mvc.perform(json(post("/api/v1/auth/register"), toJson(body)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("password"))
        .andExpect(jsonPath("$.errors[0].code").value("MaxUtf8Bytes"));

    String shortArabic = "كلمةمرورآمنة2026"; // within 72 bytes: accepted
    body.put("password", shortArabic);
    mvc.perform(json(post("/api/v1/auth/register"), toJson(body))).andExpect(status().isCreated());

    mvc.perform(json(post("/api/v1/auth/login"), toJson(Map.of("email", body.get("email"), "password", "x".repeat(100)))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    mvc.perform(json(post("/api/v1/auth/login"), toJson(Map.of("email", body.get("email"), "password", shortArabic))))
        .andExpect(status().isOk());
  }

  /** L1: an Idempotency-Key replays only in the same market. */
  @Test
  void idempotencyKeysDoNotReplayAcrossMarkets() throws Exception {
    String token = registerUser();
    UUID bakery = marketId(BAKERY);
    UUID anatolia = marketId(ANATOLIA);
    fillAndReservePickup(token, bakery, BAKERY);
    MvcResult first = mvc.perform(auth(post("/api/v1/markets/" + bakery + "/orders"), token).header("Idempotency-Key", "same")).andReturn();
    assertThat(first.getResponse().getStatus()).isEqualTo(201);

    fillAndReservePickup(token, anatolia, ANATOLIA);
    mvc.perform(auth(post("/api/v1/markets/" + anatolia + "/orders"), token).header("Idempotency-Key", "same"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    mvc.perform(auth(post("/api/v1/markets/" + bakery + "/orders"), token).header("Idempotency-Key", "same"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value((String) read(first, "$.id")));
  }

  /** L2: lowered limits and switched-off fulfilment types are re-checked at checkout. */
  @Test
  void checkoutRechecksQuantityLimitsAndOfferedFulfilment() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Recheck Testmarkt");
    UUID listing = addListing(merchant, market, "Äpfel Elstar", 2000);
    String customer = registerUser();
    setQuantity(customer, market, listing, 5);
    String slot = availableSlot(slots(null, market, "PICKUP", null), 1);
    assertThat(reserve(customer, market, "PICKUP", slot, null).getResponse().getStatus()).isEqualTo(200);

    mvc.perform(
            json(
                auth(put("/api/v1/merchant/markets/" + market + "/listings/" + listing), merchant),
                "{\"maxOrderQuantity\":2}"))
        .andExpect(status().isOk());
    mvc.perform(auth(post("/api/v1/markets/" + market + "/orders"), customer))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("MAX_QUANTITY_EXCEEDED"));

    setQuantity(customer, market, listing, 2);
    Map<String, Object> deliveryOnly = marketProfile("Recheck Testmarkt");
    @SuppressWarnings("unchecked")
    Map<String, Object> fulfilment = new HashMap<>((Map<String, Object>) deliveryOnly.get("fulfilment"));
    fulfilment.put("pickup", false);
    fulfilment.put("delivery", true);
    fulfilment.put("deliveryRadiusKm", 5);
    deliveryOnly.put("fulfilment", fulfilment);
    mvc.perform(json(auth(put("/api/v1/merchant/markets/" + market), merchant), toJson(deliveryOnly))).andExpect(status().isOk());
    mvc.perform(auth(post("/api/v1/markets/" + market + "/orders"), customer))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.code").value("FULFILMENT_NOT_OFFERED"));
  }

  /** L3: correctly signed tokens without exp (or iat) are rejected. */
  @Test
  void tokensWithoutExpiryAreRejected() throws Exception {
    NimbusJwtEncoder encoder =
        new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(new SecretKeySpec(jwtSecret.getBytes(UTF_8), "HmacSHA256")));
    String subject = userId(registerMerchant()).toString();
    JwtClaimsSet noExpiry =
        JwtClaimsSet.builder()
            .issuer(jwtIssuer)
            .subject(subject)
            .issuedAt(clock.instant().minus(1, ChronoUnit.MINUTES))
            .claim("roles", List.of("CUSTOMER", "MERCHANT"))
            .build();
    String token =
        encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), noExpiry)).getTokenValue();
    mvc.perform(auth(get("/api/v1/merchant/markets"), token)).andExpect(status().isUnauthorized());
  }

  /** L5 and hardening: amount caps, allergen bounds and promotions that are already over. */
  @Test
  void merchantInputIsBounded() throws Exception {
    String merchant = registerMerchant();
    UUID market = createMarket(merchant, "Grenzen Testmarkt");
    UUID listing = addListing(merchant, market, "Äpfel Elstar", 200);
    mvc.perform(
            json(
                auth(put("/api/v1/merchant/markets/" + market + "/listings/" + listing), merchant),
                "{\"price\":{\"amountCents\":9223372036854775000,\"currency\":\"EUR\"}}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("Max"));

    Map<String, Object> promotion = new HashMap<>();
    promotion.put("title", Map.of("de", "Vorbei"));
    promotion.put("type", "PERCENT_OFF");
    promotion.put("percentOff", 10);
    promotion.put("validFrom", "2020-01-01T00:00:00Z");
    promotion.put("validUntil", "2020-02-01T00:00:00Z");
    promotion.put("productIds", List.of(listing));
    mvc.perform(json(auth(post("/api/v1/merchant/markets/" + market + "/promotions"), merchant), toJson(promotion)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("validUntil"))
        .andExpect(jsonPath("$.errors[0].code").value("Future"));
  }

  private void fillAndReservePickup(String token, UUID market, String slug) throws Exception {
    setQuantity(token, market, availableListing(slug, 500), 4); // ≥ 20 € beats both minimum order values
    String slot = availableSlot(slots(null, market, "PICKUP", null), 1);
    assertThat(reserve(token, market, "PICKUP", slot, null).getResponse().getStatus()).isEqualTo(200);
  }

  private int remaining(UUID market, String slotId) throws Exception {
    return slots(null, market, "PICKUP", null).stream()
        .filter(s -> slotId.equals(s.get("id")))
        .map(s -> ((Number) s.get("remainingCapacity")).intValue())
        .findFirst()
        .orElseThrow();
  }
}
