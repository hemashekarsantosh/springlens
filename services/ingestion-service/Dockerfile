# ─── Build stage ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /workspace

# Copy Gradle wrapper and root build files
COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle settings.gradle
COPY build.gradle build.gradle

# Copy ALL subproject build.gradle files (no src yet) — enables dependency caching
COPY libs/shared/build.gradle libs/shared/build.gradle
COPY services/ingestion-service/build.gradle services/ingestion-service/build.gradle
COPY services/analysis-service/build.gradle services/analysis-service/build.gradle
COPY services/recommendation-service/build.gradle services/recommendation-service/build.gradle
COPY services/auth-service/build.gradle services/auth-service/build.gradle
COPY services/notification-service/build.gradle services/notification-service/build.gradle

# Resolve and cache dependencies — this layer is reused unless a build.gradle changes
RUN chmod +x gradlew && ./gradlew :services:ingestion-service:dependencies --no-daemon

# Copy source files
COPY libs/shared/src libs/shared/src
COPY services/ingestion-service/src services/ingestion-service/src

RUN ./gradlew :services:ingestion-service:bootJar -x test --no-daemon

# ─── Extract layers for efficient caching ─────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy AS extractor
WORKDIR /app
COPY --from=builder /workspace/services/ingestion-service/build/libs/*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# ─── Runtime stage (distroless) ───────────────────────────────────────────────
FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app

COPY --from=extractor /app/extracted/dependencies/          ./
COPY --from=extractor /app/extracted/spring-boot-loader/    ./
COPY --from=extractor /app/extracted/snapshot-dependencies/ ./
COPY --from=extractor /app/extracted/application/           ./

EXPOSE 8081

ENV JAVA_TOOL_OPTIONS="-XX:SharedArchiveFile=/app/app.jsa -Dspring.profiles.active=prod"

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
