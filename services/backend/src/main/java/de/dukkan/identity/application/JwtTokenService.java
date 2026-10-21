package de.dukkan.identity.application;

import de.dukkan.shared.config.DukkanProperties;
import de.dukkan.shared.security.SecurityConfiguration;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/** Issues HS256 access tokens whose subject is the user id. */
@Component
public final class JwtTokenService {

  private final JwtEncoder encoder;
  private final Clock clock;
  private final DukkanProperties.Jwt settings;

  public JwtTokenService(JwtEncoder encoder, Clock clock, DukkanProperties properties) {
    this.encoder = encoder;
    this.clock = clock;
    this.settings = properties.security().jwt();
  }

  public Duration ttl() {
    return settings.ttl();
  }

  public String issue(UserView user) {
    Instant now = clock.instant();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(settings.issuer())
            .subject(user.id().toString())
            .issuedAt(now)
            .expiresAt(now.plus(settings.ttl()))
            .claim("email", user.email())
            .claim(SecurityConfiguration.ROLES_CLAIM, user.roles())
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }
}
