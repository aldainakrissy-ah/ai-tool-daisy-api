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

# Create a directory for the jar and copy it
RUN mkdir -p /app/libs/
COPY --from=build /app/build/libs/*.jar /app/libs/

EXPOSE 8080

# Update ENTRYPOINT to use the specific jar name
ENTRYPOINT ["java", "-jar", "/app/libs/ai-tool-daisy-api-0.0.1-SNAPSHOT.jar"]