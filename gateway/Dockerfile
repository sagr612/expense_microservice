# ==============================================================================
# Stage 1: Build Jar using Maven
# ==============================================================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

# Copy Maven wrapper and pom.xml
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw

# Copy source code and package application (skipping tests for fast container build)
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# ==============================================================================
# Stage 2: Minimal Production Runtime
# ==============================================================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Run as non-root user and pre-create log directory with permissions
RUN addgroup -S appgroup && adduser -S appuser -G appgroup \
    && mkdir -p /app/logs \
    && chown -R appuser:appgroup /app

USER appuser

# Copy jar from builder stage
COPY --from=builder --chown=appuser:appgroup /workspace/target/*.jar app.jar

EXPOSE 8080

# Production-tuned JVM flags for containers
ENTRYPOINT ["java", \
    "-XX:+UseG1GC", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
