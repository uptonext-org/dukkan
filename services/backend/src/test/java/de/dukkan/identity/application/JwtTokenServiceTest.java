package de.dukkan.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import de.dukkan.shared.config.DukkanProperties;
import de.dukkan.shared.security.SecurityConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtTokenServiceTest {

  private static final String SECRET = "test-secret-0123456789abcdef0123456789";
  private static final Instant NOW = Instant.parse("2026-10-19T08:00:00Z");
  private static final Clock FIXED = Clock.fixed(NOW, ZoneOffset.UTC);
  private final UserView user = new UserView(UUID.randomUUID(), "anna@example.org", "Anna", "Muster", java.util.List.of("CUSTOMER", "MERCHANT"), "de");

  private static DukkanProperties properties(String secret, Duration ttl) {
    return new DukkanProperties(
        new DukkanProperties.Features(false, true),
        new DukkanProperties.Fulfilment(7, Duration.ofHours(1), 6, 10, Duration.ofHours(2), Duration.ofHours(1)),
        new DukkanProperties.Catalog(14, 24),
        new DukkanProperties.Markets(10, 50),
        new DukkanProperties.Security(new DukkanProperties.Jwt(secret, ttl, "dukkan")));
  }

  private static NimbusJwtDecoder decoder(String secret) {
    return decoder(secret, FIXED);
  }

  private static NimbusJwtDecoder decoder(String secret, Clock clock) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
    decoder.setJwtValidator(SecurityConfiguration.jwtValidator("dukkan", clock));
    return decoder;
  }

  private static JwtTokenService service(Clock clock) {
    NimbusJwtEncoder encoder =
        new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(SECRET.getBytes(StandardCharsets.UTF_8)));
    return new JwtTokenService(encoder, clock, properties(SECRET, Duration.ofHours(1)));
  }

  @Test
  void issuesAnHs256TokenWithSubjectIssuerAndExpiry() {
    String token = service(FIXED).issue(user);

    Jwt jwt = decoder(SECRET).decode(token);
    assertThat(jwt.getSubject()).isEqualTo(user.id().toString());
    assertThat(jwt.getClaimAsString("iss")).isEqualTo("dukkan");
    assertThat(jwt.getClaimAsString("email")).isEqualTo("anna@example.org");
    assertThat(jwt.getClaimAsStringList("roles")).containsExactly("CUSTOMER", "MERCHANT");
    assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
    assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofHours(1));
  }

  @Test
  void tokensAreValidUntilTheyExpire() {
    String token = service(FIXED).issue(user);

    Clock fiftyMinutesLater = Clock.fixed(NOW.plus(Duration.ofMinutes(50)), ZoneOffset.UTC);
    assertThat(decoder(SECRET, fiftyMinutesLater).decode(token).getSubject()).isEqualTo(user.id().toString());
    Clock twoHoursLater = Clock.fixed(NOW.plus(Duration.ofHours(2)), ZoneOffset.UTC);
    assertThatThrownBy(() -> decoder(SECRET, twoHoursLater).decode(token)).isInstanceOf(JwtException.class);
  }

  @Test
  void tokensSignedWithAnotherSecretAreRejected() {
    String token = service(FIXED).issue(user);

    assertThatThrownBy(() -> decoder("another-secret-0123456789abcdef012345").decode(token))
        .isInstanceOf(JwtException.class);
  }

  @Test
  void secretShorterThan32BytesFailsFast() {
    assertThatThrownBy(() -> properties("too-short", Duration.ofHours(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least 32 bytes");
    assertThatThrownBy(() -> properties(null, Duration.ofHours(1))).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> properties(SECRET, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void secretIsNotPrintedInToString() {
    assertThat(new DukkanProperties.Jwt(SECRET, Duration.ofHours(1), "dukkan").toString()).doesNotContain(SECRET);
  }
}
