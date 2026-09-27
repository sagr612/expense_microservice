# Stage 1: Build Jar using Maven
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

# Copy Maven wrapper and pom.xml
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw

# Copy source code and package application
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Minimal Production Runtime
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Run as non-root user and pre-create log directory
RUN addgroup -S appgroup && adduser -S appuser -G appgroup \
    && mkdir -p /app/logs \
    && chown -R appuser:appgroup /app

USER appuser

# Copy jar from builder stage
COPY --from=builder --chown=appuser:appgroup /workspace/target/*.jar app.jar

EXPOSE 8761

HEALTHCHECK --interval=10s --timeout=5s --start-period=25s --retries=3 \
  CMD wget -q --spider http://localhost:8761/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
