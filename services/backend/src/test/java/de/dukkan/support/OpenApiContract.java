package de.dukkan.support;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.yaml.snakeyaml.Yaml;

/** Reads the shared OpenAPI contract (path passed by Gradle as system property). */
public final class OpenApiContract {

  public static final String PROPERTY = "dukkan.contract.openapi";
  private static final Set<String> HTTP_METHODS = Set.of("get", "put", "post", "delete", "patch", "head", "options");

  /** One documented operation, e.g. {@code GET /api/v1/products/{}}. */
  public record Operation(String method, String path) {
    @Override
    public String toString() {
      return method + " " + path;
    }
  }

  private final Map<String, Object> root;

  private OpenApiContract(Map<String, Object> root) {
    this.root = root;
  }

  public static OpenApiContract load() {
    String location = System.getProperty(PROPERTY, "../../packages/shared-contracts/openapi.yaml");
    try (InputStream in = Files.newInputStream(Path.of(location))) {
      Map<String, Object> root = new Yaml().load(in);
      return new OpenApiContract(root);
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read OpenAPI contract at " + location, e);
    }
  }

  /** Base path from the first server URL, e.g. {@code /api/v1}. */
  public String basePath() {
    List<Map<String, Object>> servers = cast(root.get("servers"));
    String url = (String) servers.getFirst().get("url");
    return java.net.URI.create(url).getPath();
  }

  /** All operations with the base path prepended and path variables normalised to {@code {}}. */
  public Set<Operation> operations() {
    Map<String, Map<String, Object>> paths = cast(root.get("paths"));
    Set<Operation> operations = new LinkedHashSet<>();
    paths.forEach(
        (path, item) ->
            item.keySet().stream()
                .filter(HTTP_METHODS::contains)
                .forEach(m -> operations.add(new Operation(m.toUpperCase(Locale.ROOT), normalise(basePath() + path)))));
    return operations;
  }

  /** Required property names of a component schema, following {@code allOf} and {@code $ref}. */
  public Set<String> requiredFields(String schemaName) {
    Set<String> required = new LinkedHashSet<>();
    collectRequired(schema(schemaName), required);
    return required;
  }

  public static String normalise(String path) {
    return path.replaceAll("\\{[^}]+}", "{}");
  }

  private Map<String, Object> schema(String name) {
    Map<String, Object> components = cast(root.get("components"));
    Map<String, Object> schemas = cast(components.get("schemas"));
    Map<String, Object> schema = cast(schemas.get(name));
    if (schema == null) {
      throw new IllegalArgumentException("Unknown schema " + name);
    }
    return schema;
  }

  private void collectRequired(Map<String, Object> schema, Set<String> into) {
    Object ref = schema.get("$ref");
    if (ref instanceof String r) {
      collectRequired(schema(r.substring(r.lastIndexOf('/') + 1)), into);
    }
    List<String> required = cast(schema.get("required"));
    if (required != null) {
      into.addAll(required);
    }
    List<Map<String, Object>> allOf = cast(schema.get("allOf"));
    if (allOf != null) {
      allOf.forEach(part -> collectRequired(part, into));
    }
  }

  @SuppressWarnings({"TypeParameterUnusedInFormals", "unchecked"})
  private static <T> T cast(Object value) {
    return (T) value;
  }
}
