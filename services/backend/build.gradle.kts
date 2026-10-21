import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

buildscript {
    // Also lock the plugin classpath (buildscript-gradle.lockfile).
    configurations.classpath {
        resolutionStrategy.activateDependencyLocking()
    }
}

plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.errorprone)
}

group = "de.dukkan"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

dependencyLocking {
    lockAllConfigurations()
}

dependencies {
    // Spring Boot's BOM manages every transitive version; nothing below overrides it.
    implementation(platform(libs.spring.boot.bom))
    testImplementation(platform(libs.spring.boot.bom))
    testRuntimeOnly(platform(libs.spring.boot.bom))
    developmentOnly(platform(libs.spring.boot.bom))

    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.springdoc.webmvc.ui)
    implementation(libs.anthropic.java)
    runtimeOnly(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.security.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.archunit.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)

    errorprone(libs.errorprone.core)
    errorprone(libs.errorprone.support.contrib)
    errorprone(libs.errorprone.support.refaster)
}

tasks.jar {
    enabled = false
}

tasks.bootJar {
    archiveFileName = "dukkan-backend.jar"
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:all,-processing,-serial", "-Werror"))
    options.errorprone {
        disableWarningsInGeneratedCode = true
        // Refaster rules that rewrite JDK code to Guava (ImmutableList, Preconditions, ...) are skipped:
        // Guava is not a dependency of this service.
        errorproneArgs.add("-XepOpt:Refaster:NamePattern=^(?!.*(Immutable[A-Za-z]*Rules|PreconditionsRules|StringRules.Utf8EncodedLength)).*")
        if (providers.gradleProperty("errorpronePatch").isPresent) {
            // ./gradlew compileJava -PerrorpronePatch applies Error Prone's suggested fixes in place.
            errorproneArgs.addAll(
                "-XepPatchChecks:LexicographicalAnnotationListing,LexicographicalPermitsListing,StaticImport,Refaster",
                "-XepPatchLocation:IN_PLACE",
            )
        }
    }
}

tasks.named<JavaCompile>("compileTestJava") {
    options.errorprone {
        // Tests deliberately use fixture literals and AssertJ chains that a few checks flag as noise.
        check("JUnitClassModifiers", CheckSeverity.OFF)
        check("JUnitMethodDeclaration", CheckSeverity.OFF)
    }
}

tasks.test {
    useJUnitPlatform()
    systemProperty(
        "dukkan.contract.openapi",
        layout.projectDirectory.file("../../packages/shared-contracts/openapi.yaml").asFile.absolutePath,
    )
    inputs.file("../../packages/shared-contracts/openapi.yaml").withPathSensitivity(PathSensitivity.RELATIVE)
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.register("resolveAndLockAll") {
    description = "Resolves every resolvable configuration so that --write-locks records them all."
    notCompatibleWithConfigurationCache("Resolves configurations at execution time")
    doFirst {
        require(gradle.startParameter.isWriteDependencyLocks) { "Run with --write-locks" }
    }
    doLast {
        configurations.filter { it.isCanBeResolved }.forEach { it.resolve() }
    }
}
