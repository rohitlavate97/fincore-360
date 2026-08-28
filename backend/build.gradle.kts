// FinCore 360 — backend build
//
// Every version below was verified against official sources on 2026-08-28,
// not assumed. See ARCHITECTURE.md §8 and the ADRs for the reasoning.
//
//   Spring Boot 4.1.1  — supports Java 17..26
//   Kotlin     2.3.21  — matches Boot 4.1.1's BOM exactly (see note below)
//   Gradle     9.3.0   — KGP 2.3.21's fully supported maximum
//   JDK        25      — Temurin 25.0.3 LTS
//
// Kotlin version note: Kotlin 2.4.10 is the latest stable, but Boot 4.1.1's BOM
// pins kotlin-stdlib/reflect to 2.3.21. Using 2.4.10 here without also
// overriding the BOM would compile with 2.4.10 against a 2.3.21 stdlib. We stay
// on 2.3.21 deliberately.
//
// Gradle version note: Gradle 9.7.1 is current, but the Kotlin Gradle Plugin
// 2.3.21 supports up to 9.3.0. We pin the wrapper to 9.3.0 rather than run
// outside the supported matrix.

plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    kotlin("plugin.jpa") version "2.3.21"
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    jacoco
}

group = "com.fincore"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.compileJava {
    options.release.set(21)
}

tasks.compileTestJava {
    options.release.set(21)
}

repositories {
    mavenCentral()
}

dependencies {    // ── Security & Authentication (Phase 3) ──────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    testImplementation("org.springframework.security:spring-security-test")

    // ── Web / API ────────────────────────────────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")

    // ── Persistence ──────────────────────────────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // ── Kotlin ───────────────────────────────────────────────────────────
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    // Boot 4 defaults to Jackson 3 — group is tools.jackson, NOT com.fasterxml
    implementation("tools.jackson.module:jackson-module-kotlin")

    // ── API documentation (ADR: OpenAPI generated from the implementation) ─
    // springdoc 3.x is the Spring Boot 4 line; 2.x targets Boot 3.
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0")

    // ── Test ─────────────────────────────────────────────────────────────
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Architecture tests. Boot 4 ships JUnit 6, so this MUST be archunit-junit6;
    // archunit-junit5 will not bind to the JUnit Platform 6 engine.
    testImplementation("com.tngtech.archunit:archunit-junit6:1.5.0")

    testImplementation("io.mockk:mockk:1.14.11")

    // Real PostgreSQL for repository and migration tests, without Docker.
    // This machine has no Docker/WSL/Podman, so Testcontainers cannot run here.
    // zonky embedded-postgres runs an actual PostgreSQL binary — NOT an
    // in-memory substitute like H2, which could not prove SELECT ... FOR UPDATE,
    // NUMERIC(19,4) semantics, or trigger behaviour. See TESTING.md §2.
    testImplementation(platform("io.zonky.test.postgres:embedded-postgres-binaries-bom:18.6.0"))
    testImplementation("io.zonky.test:embedded-postgres:2.2.2")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

// Hibernate proxies require non-final classes; Kotlin classes are final by
// default. See ADR-015 for why this friction is an accepted cost of Kotlin+JPA.
allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
    finalizedBy(tasks.jacocoTestReport)
}

jacoco {
    toolVersion = "0.8.12"
}

tasks.jacocoTestReport {
    dependsOn(tasks.withType<Test>())
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
