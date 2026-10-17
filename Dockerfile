# ==========================================
# Stage 1: Build the application artifact
# ==========================================
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy gradle wrapper and build definitions first to leverage Docker layer caching
COPY gradlew ./
COPY gradle/ gradle/
COPY settings.gradle.kts build.gradle.kts ./

# Download dependencies (this layer is cached unless build.gradle.kts changes)
RUN ./gradlew dependencies --no-daemon || true

# Copy source code and build the executable fat JAR (skipping tests for rapid image builds)
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

# ==========================================
# Stage 2: Minimal, secure runtime container
# ==========================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Install dumb-init (proper PID 1 signal forwarding) and curl (for healthchecks)
RUN apk update \
    && apk --no-cache add curl dumb-init \
    && rm -rf /var/cache/apk/*

# Create a non-root user and group for security hardening
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy the built fat JAR from the builder stage with non-root ownership
COPY --from=builder --chown=appuser:appgroup /app/build/libs/app.jar /app/app.jar

# Switch to non-root user
USER appuser:appgroup

EXPOSE 8080

# Health check using Spring Boot Actuator
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD curl --silent --fail http://localhost:8080/actuator/health || exit 1

# dumb-init forwards SIGTERM to Java for clean, graceful application shutdown
ENTRYPOINT ["dumb-init", "--", "java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
