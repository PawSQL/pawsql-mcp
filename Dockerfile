# Use official JDK 24 image as base image
FROM openjdk:24-slim

# Set working directory
WORKDIR /app

# Copy project jar file
COPY target/pawsql-mcp-0.0.1-SNAPSHOT.jar app.jar

# Start command
CMD ["java", "-jar", "app.jar"]

# Build steps:
# 1. Build jar file
# mvn clean package

# 2. Build Docker image
# docker build -t pawsql-mcp:latest .
