package de.dukkan.shared.security;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import de.dukkan.shared.config.DukkanProperties;
import java.time.Clock;
import java.util.Objects;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless security: self-issued HS256 bearer tokens carrying a {@code roles} claim. Browsing is
 * public unless {@code dukkan.features.require-login-to-browse} is on; {@code /merchant/**} needs
 * the MERCHANT role.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

  public static final String ROLES_CLAIM = "roles";
  private static final String API = "/api/v1";

  /** Public catalog reads; they require a token when {@code require-login-to-browse} is on. */
  private static final String[] BROWSE =
      new String[] {
        API + "/markets",
        API + "/markets/*",
        API + "/markets/*/home",
        API + "/markets/*/categories",
        API + "/markets/*/products",
        API + "/markets/*/search/suggestions",
        API + "/markets/*/promotions",
        API + "/markets/*/slots",
        API + "/products/*",
        API + "/search/products"
      };

  /** Always public: languages, filter chips and the address search used during onboarding. */
  private static final String[] META =
      new String[] {
        API + "/locales", API + "/market-types", API + "/cuisines", API + "/categories", API + "/geo/search", API + "/geo/reverse"
      };

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, ProblemAuthenticationEntryPoint entryPoint, DukkanProperties properties) throws Exception {
    boolean loginToBrowse = properties.features().requireLoginToBrowse();
    http.csrf(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth -> {
              auth.requestMatchers(POST, API + "/auth/register", API + "/auth/login").permitAll();
              auth.requestMatchers(GET, META).permitAll();
              if (loginToBrowse) {
                auth.requestMatchers(GET, BROWSE).authenticated();
              } else {
                auth.requestMatchers(GET, BROWSE).permitAll();
              }
              auth.requestMatchers(API + "/merchant/**").hasRole("MERCHANT");
              auth.requestMatchers(API + "/admin/**").hasRole("ADMIN");
              auth.requestMatchers(
                      "/v3/api-docs",
                      "/v3/api-docs/**",
                      "/swagger-ui.html",
                      "/swagger-ui/**",
                      "/actuator/health",
                      "/actuator/health/**",
                      "/actuator/info",
                      "/error")
                  .permitAll();
              auth.anyRequest().authenticated();
            })
        .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(entryPoint))
        .oauth2ResourceServer(
            o ->
                o.authenticationEntryPoint(entryPoint)
                    .accessDeniedHandler(entryPoint)
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
    return http.build();
  }

  /** Maps the {@code roles} claim (e.g. {@code ["CUSTOMER","MERCHANT"]}) to {@code ROLE_*} authorities. */
  static JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
    authorities.setAuthoritiesClaimName(ROLES_CLAIM);
    authorities.setAuthorityPrefix("ROLE_");
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(authorities);
    return converter;
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  @Bean
  JwtEncoder jwtEncoder(DukkanProperties properties) {
    return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(secretKey(properties)));
  }

  @Bean
  JwtDecoder jwtDecoder(DukkanProperties properties, Clock clock) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(secretKey(properties)).macAlgorithm(MacAlgorithm.HS256).build();
    decoder.setJwtValidator(jwtValidator(properties.security().jwt().issuer(), clock));
    return decoder;
  }

  /** Validates expiry/not-before against the application clock and the expected issuer. */
  public static OAuth2TokenValidator<Jwt> jwtValidator(String issuer, Clock clock) {
    JwtTimestampValidator timestamps = new JwtTimestampValidator();
    timestamps.setClock(clock);
    // JwtTimestampValidator only checks exp when present; we never issue tokens without exp/iat, so require them.
    OAuth2TokenValidator<Jwt> hasExpiry = new JwtClaimValidator<Object>(JwtClaimNames.EXP, Objects::nonNull);
    OAuth2TokenValidator<Jwt> hasIssuedAt = new JwtClaimValidator<Object>(JwtClaimNames.IAT, Objects::nonNull);
    return new DelegatingOAuth2TokenValidator<>(timestamps, hasExpiry, hasIssuedAt, new JwtIssuerValidator(issuer));
  }

  private static SecretKey secretKey(DukkanProperties properties) {
    byte[] secret = properties.security().jwt().secret().getBytes(UTF_8);
    return new SecretKeySpec(secret, "HmacSHA256");
  }
}
