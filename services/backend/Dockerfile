# syntax=docker/dockerfile:1
# Multi-stage build for the Dukkan backend. Tests are not run here (they need Docker for
# Testcontainers); run `./gradlew build` in CI before building the image.

FROM eclipse-temurin:26.0.2.1_1-jdk-noble@sha256:26d6a81be420aa8debd8618b58b27755663ba36f2418d7196e91f1c0aedc1809 AS build
WORKDIR /workspace
# Wrapper and build scripts first so the dependency layer is cached independently of sources.
COPY gradlew settings.gradle.kts build.gradle.kts gradle.lockfile settings-gradle.lockfile buildscript-gradle.lockfile ./
COPY gradle gradle
RUN ./gradlew --no-daemon --quiet dependencies --configuration productionRuntimeClasspath > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar \
 && java -Djarmode=tools -jar build/libs/dukkan-backend.jar extract --layers --launcher --destination /workspace/extracted

FROM eclipse-temurin:26.0.2.1_1-jre-noble@sha256:7f09cd24daab29ca40ee171f8a8c77c78524ca80f489e1d97aacb9a16f9af67e AS runtime
RUN groupadd --system --gid 10001 dukkan \
 && useradd --system --uid 10001 --gid dukkan --no-create-home --shell /usr/sbin/nologin dukkan \
 && install -d -o 10001 -g 10001 -m 0750 /var/lib/dukkan/media
WORKDIR /app
COPY --from=build --chown=dukkan:dukkan /workspace/extracted/dependencies/ ./
COPY --from=build --chown=dukkan:dukkan /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=dukkan:dukkan /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=dukkan:dukkan /workspace/extracted/application/ ./
# Uploaded images (dukkan.media.directory in the docker profile). A fresh named volume copies this ownership.
VOLUME /var/lib/dukkan/media
USER 10001:10001
ENV SPRING_PROFILES_ACTIVE=docker
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "org.springframework.boot.loader.launch.JarLauncher"]
