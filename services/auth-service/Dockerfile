# ─── Build stage ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /workspace

COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle settings.gradle
COPY build.gradle build.gradle

COPY libs/shared/build.gradle libs/shared/build.gradle
COPY services/ingestion-service/build.gradle services/ingestion-service/build.gradle
COPY services/analysis-service/build.gradle services/analysis-service/build.gradle
COPY services/recommendation-service/build.gradle services/recommendation-service/build.gradle
COPY services/auth-service/build.gradle services/auth-service/build.gradle
COPY services/notification-service/build.gradle services/notification-service/build.gradle

RUN chmod +x gradlew && ./gradlew :services:auth-service:dependencies --no-daemon

COPY libs/shared/src libs/shared/src
COPY services/auth-service/src services/auth-service/src

RUN ./gradlew :services:auth-service:bootJar -x test --no-daemon

# ─── Extract layers ───────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy AS extractor
WORKDIR /app
COPY --from=builder /workspace/services/auth-service/build/libs/*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# ─── Runtime stage ────────────────────────────────────────────────────────────
FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app

COPY --from=extractor /app/extracted/dependencies/          ./
COPY --from=extractor /app/extracted/spring-boot-loader/    ./
COPY --from=extractor /app/extracted/snapshot-dependencies/ ./
COPY --from=extractor /app/extracted/application/           ./

EXPOSE 8084

ENV JAVA_TOOL_OPTIONS="-XX:SharedArchiveFile=/app/app.jsa -Dspring.profiles.active=prod"

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
