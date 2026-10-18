package de.dukkan.shared.security;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import de.dukkan.shared.config.DukkanProperties;
import java.time.Clock;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

/** Stateless security: self-issued HS256 bearer tokens, public endpoints as per the contract. */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

  private static final String API = "/api/v1";

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, ProblemAuthenticationEntryPoint entryPoint)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(POST, API + "/auth/register", API + "/auth/login")
                    .permitAll()
                    .requestMatchers(
                        GET,
                        API + "/categories",
                        API + "/products",
                        API + "/products/*",
                        API + "/search/suggestions",
                        API + "/promotions",
                        API + "/delivery-areas/check")
                    .permitAll()
                    .requestMatchers(
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/actuator/health",
                        "/actuator/health/**",
                        "/actuator/info",
                        "/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(entryPoint))
        .oauth2ResourceServer(
            o -> o.authenticationEntryPoint(entryPoint).accessDeniedHandler(entryPoint).jwt(_ -> {}));
    return http.build();
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
    return new DelegatingOAuth2TokenValidator<>(timestamps, new JwtIssuerValidator(issuer));
  }

  private static SecretKey secretKey(DukkanProperties properties) {
    byte[] secret = properties.security().jwt().secret().getBytes(UTF_8);
    return new SecretKeySpec(secret, "HmacSHA256");
  }
}
