# Use official JDK 17 image as base image
FROM openjdk:17-slim

# Set working directory
WORKDIR /app

# Copy project jar file
COPY target/pawsql-mcp-server-0.0.1-SNAPSHOT.jar app.jar

# Start command
CMD ["java", "-jar", "app.jar"]

# Build steps:
# 1. Build jar file
# mvn clean package

# 2. Build Docker image
# docker build -t pawsql/pawsql-mcp-server:latest .
# docker buildx build --platform linux/amd64,linux/arm64 -t pawsql/pawsql-mcp-server:0.1.0_beta -t pawsql/pawsql-mcp-server:latest --push .