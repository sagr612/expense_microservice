# ==============================================================================
# Stage 1: Build Jar using Gradle
# ==============================================================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

# Copy Gradle wrapper and configuration files first (for layer caching)
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle

RUN chmod +x gradlew

# Copy source code and build the fat jar (skip tests for container build)
COPY src ./src
RUN ./gradlew bootJar -x test --no-daemon

# ==============================================================================
# Stage 2: Minimal Production Runtime
# ==============================================================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Run as non-root user and pre-create log directory
RUN addgroup -S appgroup && adduser -S appuser -G appgroup \
    && mkdir -p /app/logs \
    && chown -R appuser:appgroup /app

USER appuser

# Copy jar from builder stage
COPY --from=builder --chown=appuser:appgroup /workspace/build/libs/*.jar app.jar

EXPOSE 9001

ENTRYPOINT ["java", \
    "-XX:+UseG1GC", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
