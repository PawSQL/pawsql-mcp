# PawSQL MCP Server - Multi-User Deployment Guide

This document provides instructions for deploying the PawSQL MCP Server in multi-user mode using Docker or Docker Compose.

## Overview

The PawSQL MCP Server has been enhanced to support multiple users in a secure, isolated manner. Key features include:

- Session-based authentication with configurable timeout
- User data isolation
- Permission controls for different resources
- Audit logging of security events and user actions
- Support for API key or session token authentication
- Compatibility with Claude Desktop client and other tools

## Deployment Options

### Option 1: Using Docker Compose (Recommended)

1. Clone the repository or download the source code
2. Configure environment variables in `docker-compose.yml`
3. Run the following command:

```bash
docker-compose up -d
```

This will build and start the PawSQL MCP Server container with the specified configuration.

### Option 2: Using Docker

1. Build the Docker image:

```bash
mvn clean package
docker build -t pawsql/pawsql-mcp-server:latest .
```

2. Run the Docker container:

```bash
docker run -d --name pawsql-mcp-server -p 8080:8080 \
  -v ./config:/app/config \
  -v ./logs:/app/logs \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e PAWSQL_ADMIN_USERS=admin@example.com \
  -e PAWSQL_SESSION_TIMEOUT_HOURS=24 \
  pawsql/pawsql-mcp-server:latest
```

## Configuration Options

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `PAWSQL_ADMIN_USERS` | Comma-separated list of admin user emails | None |
| `PAWSQL_READONLY_USERS` | Comma-separated list of read-only user emails | None |
| `PAWSQL_SESSION_TIMEOUT_HOURS` | Session timeout in hours | 24 |
| `PAWSQL_SESSION_CLEANUP_INTERVAL_MINUTES` | Interval for cleaning up expired sessions | 60 |
| `PAWSQL_SESSION_MAX_PER_USER` | Maximum number of sessions per user | 5 |
| `PAWSQL_API_BASE_URL` | Default API base URL for fallback | https://www.pawsql.com |
| `PAWSQL_EDITION` | Default PawSQL edition for fallback | cloud |
| `PAWSQL_API_EMAIL` | Default API email for fallback | None |
| `PAWSQL_API_PASSWORD` | Default API password for fallback | None |

### Custom Configuration File

You can provide a custom `application.properties` file by mounting it to `/app/config/application.properties`:

```bash
docker run -d --name pawsql-mcp-server -p 8080:8080 \
  -v ./custom-config/application.properties:/app/config/application.properties \
  pawsql/pawsql-mcp-server:latest
```

## Client Configuration

### Claude Desktop Client

To connect the Claude Desktop client to the multi-user MCP server:

1. Configure the client to use the MCP server URL: `http://your-server:8080/api/v1/sse`
2. Add authentication parameters:
   - Option 1: Add `?sessionId=your-session-id` to the URL
   - Option 2: Add `?apiKey=your-api-key` to the URL
   - Option 3: Set HTTP header `X-Session-ID: your-session-id`
   - Option 4: Set HTTP header `X-API-Key: your-api-key`

### Authentication Endpoint

To obtain a session ID, make a POST request to the authentication endpoint:

```
POST /api/v1/auth?email=user@example.com&password=userpassword&edition=cloud
```

The response will contain a session ID that can be used for subsequent requests.

## Security Considerations

1. In production, enable HTTPS by configuring SSL in `application.properties`
2. Use a reverse proxy (like Nginx) for additional security layers
3. Configure proper firewall rules to restrict access to the server
4. Regularly review audit logs for suspicious activity
5. Rotate API keys periodically

## Monitoring and Maintenance

- Logs are stored in the `/app/logs` directory
- Health check endpoint: `http://your-server:8080/actuator/health`
- Session management endpoint (admin only): `http://your-server:8080/api/v1/admin/sessions`

## Troubleshooting

### Common Issues

1. **Authentication failures**: Check that the email and password are correct
2. **Connection issues**: Ensure the server is accessible from the client
3. **Permission denied**: Verify that the user has the required permissions

For additional support, please contact PawSQL support.
