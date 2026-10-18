# Versions

Verified on 2026-10-18. "Maven Central" means `https://repo1.maven.org/maven2/<group>/<artifact>/maven-metadata.xml`
(or the artifact's POM); "Boot BOM" means `spring-boot-dependencies-4.1.1.pom`. Transitive versions are taken
from the Boot BOM and are not overridden. Exact resolved versions are in `gradle.lockfile`
(plus `buildscript-gradle.lockfile` for Gradle plugins).

| Component | Requested | Used | Verification source | Notes |
|---|---|---|---|---|
| JDK (toolchain) | Temurin 25 | Temurin 25.0.4.1+1 | `java -version` | `java.toolchain.languageVersion = 25`, `options.release = 25`. |
| Gradle | 9.8.1 | 9.8.1 (wrapper) | `gradle --version`, `gradle-wrapper.properties` | `distributionSha256Sum` is **self-computed** from the downloaded zip: the published `.sha256` URL returned 403 through the sandbox proxy. Re-verify against gradle.org/release-checksums. |
| Spring Boot | 4.1.1 | 4.1.1 | Maven Central (`spring-boot` metadata: 4.1.1 is the latest GA; 4.2.0-M2 is a milestone) | Applied as `platform(spring-boot-dependencies)` (Gradle-native BOM import), not the dependency-management plugin. |
| Spring Framework / Security | (via Boot) | 7.0.9 / 7.1.1 | Boot BOM | |
| Hibernate ORM | (via Boot) | 7.4.5.Final | Boot BOM | |
| Jackson | (via Boot) | 3.1.5 (`tools.jackson`) | Boot BOM | Boot 4 defaults to Jackson 3. `spring.jackson.serialization.write-dates-as-timestamps` is not bindable any more; Jackson 3 writes ISO-8601 by default, so the property was dropped. |
| reactor-bom | 2025.0.7 (BOM-managed) | managed, not on classpath | Boot BOM | No Reactor/WebFlux by design (servlet stack). |
| spring-amqp | 4.1.1 (BOM-managed) | managed, not on classpath | Boot BOM | No RabbitMQ by design. |
| MongoDB driver | 5.8.1 (BOM-managed) | managed, not on classpath | Boot BOM | No MongoDB by design. |
| Flyway | 12.4.0 | 12.4.0 (`flyway-core`, `flyway-database-postgresql`) | Boot BOM, `gradle.lockfile` | Boot 4 needs `spring-boot-starter-flyway` (auto-configuration moved to its own module). |
| PostgreSQL JDBC | 42.7.13 | 42.7.13 | Boot BOM, `gradle.lockfile` | |
| PostgreSQL server | 18.6 | `postgres:18.6` | Docker Hub tags API (HTTP 200), Testcontainers run | |
| Testcontainers | 2.0.5 | 2.0.5 | Boot BOM, `gradle.lockfile` | 2.x renamed artifacts (`testcontainers-postgresql`, `testcontainers-junit-jupiter`) and packages (`org.testcontainers.postgresql.PostgreSQLContainer`, non-generic). |
| JUnit | 6.0.3 | 6.0.3 | Boot BOM, `gradle.lockfile` | |
| SnakeYAML | (via Boot) | 2.6 | Boot BOM | Used by the contract test to read `openapi.yaml`; no extra dependency. |
| Nimbus JOSE+JWT | (via Spring Security) | 10.9.1 | `gradle.lockfile` | HS256 encoding/decoding through `NimbusJwtEncoder`/`NimbusJwtDecoder`. |
| OAuth2 resource server starter | `spring-boot-starter-oauth2-resource-server` | `spring-boot-starter-security-oauth2-resource-server` | Boot BOM | Boot 4 renamed the starter; the old artifact still exists only as a deprecated alias. |
| springdoc-openapi | 3.x compatible with Boot 4 | `springdoc-openapi-starter-webmvc-ui` 3.1.1 | Maven Central (latest 3.1.1; its parent POM is `spring-boot-starter-parent` 4.1.0) | Served at `/v3/api-docs` and `/swagger-ui.html`, restricted to `/api/v1/**`. The shared `openapi.yaml` stays the source of truth; the generated document is for exploration only. |
| Error Prone | 2.50.0 | 2.50.0 | Maven Central (latest) | Works on JDK 25 without manual `--add-exports`: the Gradle plugin configures the compiler JVM. |
| Error Prone Support | 0.30.0 | 0.30.0 (`error-prone-contrib`, `refaster-runner`) | Maven Central (latest) | Refaster rules that rewrite JDK code to Guava (`Immutable*Rules`, `PreconditionsRules`, `StringRules.Utf8EncodedLength`) are excluded via `-XepOpt:Refaster:NamePattern`, because Guava is not a dependency. `RequestMappingAnnotation` does not recognise `@AuthenticationPrincipal`, so controllers use `@CurrentSecurityContext SecurityContext` instead of disabling the check. |
| net.ltgt.errorprone Gradle plugin | latest compatible with Gradle 9 | 5.1.1 | Gradle Plugin Portal metadata (latest) | |
| ArchUnit | (pin in catalog) | 1.5.1 (`archunit-junit5`) | Maven Central (latest) | Reads Java 25 class files. |
| Docker build image | `eclipse-temurin:25.0.4.1_1-jdk-noble` | same, pinned by digest `sha256:ba0f0e1a…e5e` | Docker Hub tags API (HTTP 200), `docker pull` | |
| Docker runtime image | `eclipse-temurin:25.0.4.1_1-jre-noble` | same, pinned by digest `sha256:3f2223fe…3a5` | Docker Hub tags API (HTTP 200), `docker pull` | Runs as uid 10001 (`dukkan`). |

## Other compatibility notes

- Spring Framework 7 no longer defaults `ProblemDetail.type` to `about:blank` (it is `null` and omitted). The
  contract requires `type`, so `shared.error.Problems` sets `about:blank` explicitly on every problem.
- Hibernate 7 schema validation rejects `char(n)` columns mapped to `String`; the schema uses `varchar(n)`
  with CHECK constraints instead (e.g. postal codes `^[0-9]{5}$`).
- Spring Boot 4 moved test auto-configuration: `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`
  (`spring-boot-starter-webmvc-test`).
