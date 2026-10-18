# syntax=docker/dockerfile:1
# Multi-stage build for the Dukkan backend. Tests are not run here (they need Docker for
# Testcontainers); run `./gradlew build` in CI before building the image.

FROM eclipse-temurin:25.0.4.1_1-jdk-noble@sha256:ba0f0e1a33864105e1d454e88105c617ab40f71062db1f306bf58be32a3f8e5e AS build
WORKDIR /workspace
# Wrapper and build scripts first so the dependency layer is cached independently of sources.
COPY gradlew settings.gradle.kts build.gradle.kts gradle.lockfile settings-gradle.lockfile buildscript-gradle.lockfile ./
COPY gradle gradle
RUN ./gradlew --no-daemon --quiet dependencies --configuration productionRuntimeClasspath > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar \
 && java -Djarmode=tools -jar build/libs/dukkan-backend.jar extract --layers --launcher --destination /workspace/extracted

FROM eclipse-temurin:25.0.4.1_1-jre-noble@sha256:3f2223fe04b750aa5a8179e29b5c9a1ddf7665fc9bb08bfe11ad01d143bc13a5 AS runtime
RUN groupadd --system --gid 10001 dukkan \
 && useradd --system --uid 10001 --gid dukkan --no-create-home --shell /usr/sbin/nologin dukkan
WORKDIR /app
COPY --from=build --chown=dukkan:dukkan /workspace/extracted/dependencies/ ./
COPY --from=build --chown=dukkan:dukkan /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=dukkan:dukkan /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=dukkan:dukkan /workspace/extracted/application/ ./
USER 10001:10001
ENV SPRING_PROFILES_ACTIVE=docker
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "org.springframework.boot.loader.launch.JarLauncher"]
