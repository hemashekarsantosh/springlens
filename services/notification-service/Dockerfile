# ─── Build stage ──────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /workspace

COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle settings.gradle
COPY build.gradle build.gradle

COPY libs/shared/build.gradle libs/shared/build.gradle
COPY libs/shared/src libs/shared/src

COPY services/notification-service/build.gradle services/notification-service/build.gradle
COPY services/notification-service/src services/notification-service/src

RUN chmod +x gradlew && ./gradlew :services:notification-service:bootJar -x test --no-daemon

# ─── Extract layers ───────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy AS extractor
WORKDIR /app
COPY --from=builder /workspace/services/notification-service/build/libs/*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# ─── Runtime stage ────────────────────────────────────────────────────────────
FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app

COPY --from=extractor /app/extracted/dependencies/          ./
COPY --from=extractor /app/extracted/spring-boot-loader/    ./
COPY --from=extractor /app/extracted/snapshot-dependencies/ ./
COPY --from=extractor /app/extracted/application/           ./

EXPOSE 8085

ENV JAVA_TOOL_OPTIONS="-XX:SharedArchiveFile=/app/app.jsa -Dspring.profiles.active=prod"

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
