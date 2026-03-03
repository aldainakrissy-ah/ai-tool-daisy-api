ARG REGISTRY=docker.io
ARG REPO=eclipse-temurin
ARG RUN_TAG=21-jre

FROM ${REGISTRY}/${REPO}:${RUN_TAG}

WORKDIR /app

# Create non-root user for security
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Create a directory for the jar and copy it
RUN mkdir -p /app/libs/ && chown -R appuser:appuser /app

# Copy the pre-built jar file from local build
COPY build/libs/ai-tool-daisy-api-0.0.1-SNAPSHOT.jar /app/libs/
RUN chown -R appuser:appuser /app/libs/

# Switch to non-root user
USER appuser

EXPOSE 8080


# Update ENTRYPOINT to print environment variables for debugging and then run the app
ENTRYPOINT ["/bin/sh", "-c", "echo 'Attempting to start with SSH_HOST:' $SSH_HOST 'SSH_USER:' $SSH_USER 'SSH_PASS is set:' $(if [ -n \"$SSH_PASS\" ]; then echo 'true'; else echo 'false'; fi) && java -XX:+UseContainerSupport -XX:MaxRAMPercentage=70.0 -jar /app/libs/ai-tool-daisy-api-0.0.1-SNAPSHOT.jar"]