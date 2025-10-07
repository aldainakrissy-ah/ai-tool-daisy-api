FROM openjdk:21-jdk-slim as build

WORKDIR /app

# Copy gradle files first to leverage Docker cache
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

# Make gradlew executable
RUN chmod +x gradlew

# Copy source code
COPY src ./src

# Build the application
RUN ./gradlew build -x test

FROM openjdk:21-jdk-slim

WORKDIR /app

# Create non-root user for security
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Create a directory for the jar and copy it
RUN mkdir -p /app/libs/ && chown -R appuser:appuser /app

# Copy the jar file from build stage
COPY --from=build /app/build/libs/*.jar /app/libs/
RUN chown -R appuser:appuser /app/libs/

# Switch to non-root user
USER appuser

EXPOSE 8080


# Update ENTRYPOINT to use the specific jar name with optimized JVM settings
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=70.0", "-jar", "/app/libs/ai-tool-daisy-api-0.0.1-SNAPSHOT.jar"]