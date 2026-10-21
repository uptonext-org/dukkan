package de.dukkan.notification.infrastructure;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import de.dukkan.notification.application.PushGateway;
import de.dukkan.shared.config.DukkanProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Firebase Cloud Messaging HTTP v1 ({@code projects/{id}/messages:send}), which reaches Android
 * directly and iOS through the APNs key uploaded to Firebase. Authenticates with a service account:
 * a signed JWT is exchanged for an OAuth access token (cached until shortly before it expires).
 * Disabled without {@code dukkan.push.fcm.*}: notifications then only appear in the in-app inbox.
 */
@Component
final class FcmPushGateway implements PushGateway {

  private static final Logger LOG = LoggerFactory.getLogger(FcmPushGateway.class);
  private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  private record ServiceAccount(String clientEmail, PrivateKey key, String tokenUri) {}

  private record AccessToken(String value, Instant expiresAt) {}

  private final DukkanProperties.Fcm settings;
  private final JsonMapper json;
  private final Clock clock;
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
  private final @Nullable ServiceAccount account;
  private @Nullable AccessToken token;

  FcmPushGateway(DukkanProperties properties, JsonMapper json, Clock clock) {
    this.settings = properties.push().fcm();
    this.json = json;
    this.clock = clock;
    this.account = settings.enabled() ? load(settings, json) : null;
    if (account != null) {
      LOG.info("Push via Firebase Cloud Messaging enabled for project {}", settings.projectId());
    }
  }

  private static ServiceAccount load(DukkanProperties.Fcm settings, JsonMapper json) {
    try {
      JsonNode node = json.readTree(Files.readString(requireNonNull(settings.credentialsFile()), UTF_8));
      String pem = node.get("private_key").asString();
      byte[] der =
          Base64.getMimeDecoder().decode(pem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", ""));
      PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
      String tokenUri =
          settings.tokenUri() != null ? settings.tokenUri() : node.has("token_uri") ? node.get("token_uri").asString() : "https://oauth2.googleapis.com/token";
      return new ServiceAccount(node.get("client_email").asString(), key, tokenUri);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read dukkan.push.fcm.credentials-file", e);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Invalid private key in dukkan.push.fcm.credentials-file", e);
    }
  }

  @Override
  public boolean enabled() {
    return account != null;
  }

  @Override
  public List<String> send(List<String> deviceTokens, PushMessage message) {
    if (account == null || deviceTokens.isEmpty()) {
      return List.of();
    }
    List<String> invalid = new ArrayList<>();
    try {
      String bearer = accessToken(account);
      for (String device : deviceTokens) {
        HttpResponse<String> response =
            http.send(
                HttpRequest.newBuilder(URI.create(settings.apiBaseUrl() + "/v1/projects/" + settings.projectId() + "/messages:send"))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + bearer)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body(device, message))))
                    .build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 404 || (response.statusCode() == 400 && response.body().contains("registration token"))) {
          invalid.add(device);
        } else if (response.statusCode() >= 300) {
          LOG.warn("FCM answered {} for a push", response.statusCode());
        }
      }
    } catch (IOException | RuntimeException e) {
      LOG.warn("Push not sent: {}", e.toString());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return invalid;
  }

  private static Map<String, Object> body(String device, PushMessage message) {
    Map<String, Object> inner = new LinkedHashMap<>();
    inner.put("token", device);
    inner.put("notification", Map.of("title", message.title(), "body", message.body()));
    inner.put("data", message.data());
    inner.put("android", Map.of("priority", "high"));
    inner.put("apns", Map.of("payload", Map.of("aps", Map.of("sound", "default"))));
    return Map.of("message", inner);
  }

  private synchronized String accessToken(ServiceAccount account) throws IOException, InterruptedException {
    Instant now = clock.instant();
    if (token != null && token.expiresAt().isAfter(now.plusSeconds(60))) {
      return token.value();
    }
    String assertion;
    try {
      SignedJWT jwt =
          new SignedJWT(
              new JWSHeader(JWSAlgorithm.RS256),
              new JWTClaimsSet.Builder()
                  .issuer(account.clientEmail())
                  .audience(account.tokenUri())
                  .claim("scope", SCOPE)
                  .issueTime(Date.from(now))
                  .expirationTime(Date.from(now.plusSeconds(3600)))
                  .build());
      jwt.sign(new RSASSASigner(account.key()));
      assertion = jwt.serialize();
    } catch (JOSEException e) {
      throw new IllegalStateException("Cannot sign the FCM token request", e);
    }
    String form = "grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", UTF_8) + "&assertion=" + assertion;
    HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder(URI.create(account.tokenUri()))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      throw new IllegalStateException("FCM token endpoint answered " + response.statusCode());
    }
    JsonNode node = json.readTree(response.body());
    token = new AccessToken(node.get("access_token").asString(), now.plusSeconds(node.path("expires_in").asLong(3600)));
    return token.value();
  }
}
