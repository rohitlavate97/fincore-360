# FinCore 360 — backend image
#
# NOT VERIFIED: never built. No Docker daemon on the authoring machine.

# ── build stage ──────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk-alpine AS build

WORKDIR /build

# Dependency layer first — these change far less often than source, so a source
# edit does not re-download the world.
COPY backend/gradle ./gradle
COPY backend/gradlew backend/settings.gradle.kts backend/build.gradle.kts ./
RUN chmod +x ./gradlew && ./gradlew dependencies --no-daemon || true

COPY backend/src ./src
RUN ./gradlew bootJar --no-daemon -x test

# ── runtime stage ────────────────────────────────────────────────────────
# No build toolchain in the runtime image.
FROM eclipse-temurin:25-jre-alpine AS runtime

RUN apk add --no-cache wget && \
    addgroup -S fincore && adduser -S fincore -G fincore

WORKDIR /app
COPY --from=build /build/build/libs/*.jar app.jar

# Never run as root.
USER fincore

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --start-period=40s --retries=5 \
    CMD wget --spider -q http://localhost:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
