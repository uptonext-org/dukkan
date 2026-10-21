package de.dukkan.media.infrastructure;

import de.dukkan.media.application.MediaKeys;
import de.dukkan.media.application.MediaObject;
import de.dukkan.media.application.MediaStorage;
import de.dukkan.media.domain.ImageFormat;
import de.dukkan.shared.config.DukkanProperties;
import static java.util.Objects.requireNonNull;

import java.net.URI;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Stores media in an S3-compatible bucket ({@code dukkan.media.storage=s3}, B-32a), under the same
 * sharded keys as the filesystem store ({@code ab/abcd….jpg}). Objects are written once with
 * {@code If-None-Match: *} and {@code Cache-Control: public, max-age=31536000, immutable}, so a CDN
 * or the bucket's public endpoint can serve them directly; point {@code dukkan.media.public-base-url}
 * there and {@code GET /media/{key}} is no longer hit. Works with several backend instances.
 */
@Component
@ConditionalOnProperty(name = "dukkan.media.storage", havingValue = "s3")
public class S3MediaStorage implements MediaStorage, DisposableBean {

  static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

  private static final Logger LOG = LoggerFactory.getLogger(S3MediaStorage.class);

  private final S3Client client;
  private final String bucket;

  public S3MediaStorage(DukkanProperties properties) {
    DukkanProperties.S3 s3 = properties.media().s3();
    this.bucket = requireNonNull(s3.bucket());
    var builder =
        S3Client.builder()
            .region(Region.of(s3.region()))
            .httpClient(UrlConnectionHttpClient.create())
            .credentialsProvider(credentials(s3))
            .forcePathStyle(s3.pathStyle());
    if (s3.endpoint() != null) {
      builder.endpointOverride(URI.create(s3.endpoint()));
    }
    this.client = builder.build();
    if (s3.createBucket()) {
      createBucketIfMissing();
    }
    LOG.info("Media storage: S3 bucket {} ({})", bucket, s3.endpoint() == null ? "AWS " + s3.region() : s3.endpoint());
  }

  private static AwsCredentialsProvider credentials(DukkanProperties.S3 s3) {
    if (s3.accessKey() != null && s3.secretKey() != null) {
      return StaticCredentialsProvider.create(AwsBasicCredentials.create(s3.accessKey(), s3.secretKey()));
    }
    return DefaultCredentialsProvider.builder().build();
  }

  private void createBucketIfMissing() {
    try {
      client.headBucket(b -> b.bucket(bucket));
    } catch (S3Exception e) {
      if (e.statusCode() != 404) {
        throw e;
      }
      client.createBucket(b -> b.bucket(bucket));
      LOG.info("Created bucket {}", bucket);
    }
  }

  @Override
  public boolean exists(String key) {
    try {
      client.headObject(HeadObjectRequest.builder().bucket(bucket).key(objectKey(key)).build());
      return true;
    } catch (NoSuchKeyException e) {
      return false;
    } catch (S3Exception e) {
      if (e.statusCode() == 404) {
        return false;
      }
      throw e;
    }
  }

  @Override
  public void put(String key, byte[] bytes, String contentType) {
    PutObjectRequest.Builder request =
        PutObjectRequest.builder().bucket(bucket).key(objectKey(key)).contentType(contentType).cacheControl(CACHE_CONTROL);
    try {
      client.putObject(request.ifNoneMatch("*").build(), RequestBody.fromBytes(bytes));
    } catch (S3Exception e) {
      if (e.statusCode() == 412 || e.statusCode() == 409) {
        // Already stored by a concurrent upload of the same bytes (content-addressed key).
        return;
      }
      if (e.statusCode() == 501) {
        // Provider without conditional writes: the key is content-addressed, so a plain overwrite is harmless.
        client.putObject(request.build(), RequestBody.fromBytes(bytes));
        return;
      }
      throw e;
    }
  }

  /** Images are at most a few megabytes (dukkan.media.max-file-size), so they are read into memory. */
  @Override
  public Optional<MediaObject> open(String key) {
    Optional<ImageFormat> format = MediaKeys.format(key);
    if (format.isEmpty()) {
      return Optional.empty();
    }
    try {
      ResponseBytes<GetObjectResponse> object =
          client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(objectKey(key)).build());
      byte[] bytes = object.asByteArray();
      return Optional.of(new MediaObject(key, format.get().contentType(), bytes.length, new ByteArrayResource(bytes)));
    } catch (NoSuchKeyException e) {
      return Optional.empty();
    } catch (S3Exception e) {
      if (e.statusCode() == 404) {
        return Optional.empty();
      }
      throw e;
    }
  }

  static String objectKey(String key) {
    if (!MediaKeys.isValid(key)) {
      throw new IllegalArgumentException("Invalid media key");
    }
    return key.substring(0, 2) + "/" + key;
  }

  @Override
  public void destroy() {
    client.close();
  }
}
