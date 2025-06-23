package com.pawsql.mcp.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a user session for the MCP server.
 * Contains user identity information and session metadata.
 */
public class UserSession {
    private final String sessionId;
    private final String apiKey;
    private final String email;
    private final String edition;
    private final String apiBaseUrl;
    private final Instant createdAt;
    private Instant lastAccessedAt;

    public UserSession(String apiKey, String email, String edition, String apiBaseUrl) {
        this.sessionId = UUID.randomUUID().toString();
        this.apiKey = apiKey;
        this.email = email;
        this.edition = edition;
        this.apiBaseUrl = apiBaseUrl;
        this.createdAt = Instant.now();
        this.lastAccessedAt = Instant.now();
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getEmail() {
        return email;
    }

    public String getEdition() {
        return edition;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void updateLastAccessedAt() {
        this.lastAccessedAt = Instant.now();
    }
}
