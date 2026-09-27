FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

# Copy Gradle wrapper and configuration files first (for Docker layer caching)
COPY gradlew settings.gradle gradle.properties ./
COPY gradle ./gradle
COPY app/build.gradle ./app/

RUN chmod +x gradlew

# Copy source code and build the fat jar (skip tests for faster container build)
COPY app/src ./app/src
RUN ./gradlew :app:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup \
    && mkdir -p /app/logs \
    && chown -R appuser:appgroup /app

USER appuser

COPY --from=builder --chown=appuser:appgroup /workspace/app/build/libs/*.jar app.jar

EXPOSE 9898

ENTRYPOINT ["java", \
    "-XX:+UseG1GC", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
