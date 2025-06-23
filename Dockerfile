# Use official JDK 24 image as base image
FROM openjdk:24-slim

# Set working directory
WORKDIR /app

# Copy project jar file
COPY target/pawsql-mcp-server-0.0.1-SNAPSHOT.jar app.jar

# Copy config directory if it exists (optional)
COPY config/ /app/config/

# Create logs directory
RUN mkdir -p /app/logs

# Expose port for SSE endpoints
EXPOSE 8080

# Set environment variables with defaults
ENV JAVA_OPTS="-Xms512m -Xmx1g" \
    SPRING_PROFILES_ACTIVE="prod" \
    SERVER_PORT="8080"

# Start command with configurable options
CMD ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar ${JAVA_ARGS}"]

# Build steps:
# 1. Build jar file
# mvn clean package

# 2. Build Docker image
# docker build -t pawsql/pawsql-mcp-server:latest .
# docker buildx build --platform linux/amd64,linux/arm64 -t pawsql/pawsql-mcp-server:0.1.0_beta -t pawsql/pawsql-mcp-server:latest --push .

# Run multi-user SSE server:
# docker run -d --name pawsql-mcp-server -p 8080:8080 \
#   -v ./config:/app/config \
#   -v ./logs:/app/logs \
#   -e SPRING_PROFILES_ACTIVE=prod \
#   -e PAWSQL_ADMIN_USERS=admin@example.com,admin2@example.com \
#   -e PAWSQL_SESSION_TIMEOUT_HOURS=24 \
#   -e PAWSQL_API_BASE_URL=https://www.pawsql.com \
#   pawsql/pawsql-mcp-server:latest