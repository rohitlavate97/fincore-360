# FinCore 360 — Backend Production Image
# Multi-stage build: Eclipse Temurin JDK 25 build -> JRE 25 Alpine runtime

# ── STAGE 1: BUILD ENVIRONMENT ──────────────────────────────────
FROM eclipse-temurin:25-jdk-alpine AS build

WORKDIR /build

# Dependency cache layer
COPY backend/gradle ./gradle
COPY backend/gradlew backend/settings.gradle.kts backend/build.gradle.kts ./
RUN chmod +x ./gradlew && ./gradlew dependencies --no-daemon || true

# Copy source and build executable bootJar without tests (tests verified in CI stage)
COPY backend/src ./src
RUN ./gradlew bootJar --no-daemon -x test

# ── STAGE 2: RUNTIME ENVIRONMENT ────────────────────────────────
# Lean JRE runtime without compiler or build tools
FROM eclipse-temurin:25-jre-alpine AS runtime

# Install wget for healthcheck probe and create non-root service user
RUN apk add --no-cache wget && \
    addgroup -S fincore && adduser -S fincore -G fincore

WORKDIR /app

# Copy executable jar from build stage
COPY --from=build --chown=fincore:fincore /build/build/libs/*.jar app.jar

# Enforce least-privilege security principle: run as non-root
USER fincore

EXPOSE 8080

# Distinct Readiness probe for container orchestrators and compose
HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=5 \
    CMD wget --spider -q http://localhost:8080/actuator/health/readiness || exit 1

# Container-aware JVM memory settings and deterministic random source
ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
