# ─── Build stage ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /workspace

# Copy Gradle wrapper and root build files
COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle settings.gradle
COPY build.gradle build.gradle

# Copy shared lib
COPY libs/shared/build.gradle libs/shared/build.gradle
COPY libs/shared/src libs/shared/src

# Copy service sources
COPY services/ingestion-service/build.gradle services/ingestion-service/build.gradle
COPY services/ingestion-service/src services/ingestion-service/src

RUN chmod +x gradlew && ./gradlew :services:ingestion-service:bootJar -x test --no-daemon

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

# CDS archive for faster startup
ENV JAVA_TOOL_OPTIONS="-XX:SharedArchiveFile=/app/app.jsa -Dspring.profiles.active=prod"

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
