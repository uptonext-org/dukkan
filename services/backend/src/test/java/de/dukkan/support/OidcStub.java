package de.dukkan.support;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * Stand-in for Google's and Apple's key endpoints: one RSA key, served as JWKS on a local port,
 * and helpers to sign ID tokens with it. {@code IntegrationTest} points both providers here.
 */
public final class OidcStub {

  public static final String GOOGLE_CLIENT = "dukkan-test.apps.googleusercontent.com";
  public static final String APPLE_CLIENT = "app.dukkan.mobile";
  public static final String GOOGLE_ISSUER = "https://accounts.google.com";
  public static final String APPLE_ISSUER = "https://appleid.apple.com";

  private static final RSAKey KEY;
  private static final HttpServer SERVER;

  static {
    try {
      KEY = new RSAKeyGenerator(2048).keyID("test-key").algorithm(JWSAlgorithm.RS256).generate();
      SERVER = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    byte[] jwks = new JWKSet(KEY.toPublicJWK()).toString().getBytes(UTF_8);
    SERVER.createContext(
        "/keys",
        exchange -> {
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, jwks.length);
          exchange.getResponseBody().write(jwks);
          exchange.close();
        });
    SERVER.setExecutor(null);
    SERVER.start();
    Runtime.getRuntime().addShutdownHook(new Thread(() -> SERVER.stop(0)));
  }

  private OidcStub() {}

  public static String jwkSetUri() {
    return "http://127.0.0.1:" + SERVER.getAddress().getPort() + "/keys";
  }

  /** A signed ID token; {@code claims} are added to iss/aud/sub/iat/exp. */
  public static String idToken(String issuer, String audience, String subject, Instant now, long ttlSeconds, Map<String, Object> claims) {
    JWTClaimsSet.Builder builder =
        new JWTClaimsSet.Builder()
            .issuer(issuer)
            .audience(audience)
            .subject(subject)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(ttlSeconds)));
    claims.forEach(builder::claim);
    SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(), builder.build());
    try {
      jwt.sign(new RSASSASigner(KEY));
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
    return jwt.serialize();
  }
}
