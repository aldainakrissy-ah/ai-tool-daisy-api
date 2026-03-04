# Build stage
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /workspace

# Copy gradle wrapper and gradle files
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY gradle.properties .

# Copy source code
COPY src src

# Make gradlew executable and build the application
RUN chmod +x gradlew && ./gradlew clean bootJar -x test

# Runtime stage
FROM eclipse-temurin:21-jre

WORKDIR /app

# Create non-root user for security
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Copy the built jar from builder stage
COPY --from=builder /workspace/build/libs/*.jar /app/app.jar
RUN chown -R appuser:appuser /app

# Switch to non-root user
USER appuser

EXPOSE 8080

# Update ENTRYPOINT to print environment variables for debugging and then run the app
ENTRYPOINT ["/bin/sh", "-c", "echo 'Attempting to start with SSH_HOST:' $SSH_HOST 'SSH_USER:' $SSH_USER 'SSH_PASS is set:' $(if [ -n \"$SSH_PASS\" ]; then echo 'true'; else echo 'false'; fi) && java -XX:+UseContainerSupport -XX:MaxRAMPercentage=70.0 -jar /app/app.jar"]
