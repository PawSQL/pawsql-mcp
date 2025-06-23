package com.pawsql.mcp.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Client-side authenticator for MCP server connections.
 * Handles automatic authentication and token management.
 */
public class McpClientAuthenticator {
    private static final Logger log = LoggerFactory.getLogger(McpClientAuthenticator.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    
    // Cache of session tokens to avoid repeated authentication
    private static final Map<String, SessionInfo> sessionCache = new ConcurrentHashMap<>();
    
    /**
     * Get an authenticated URL for connecting to the MCP server.
     * This method will automatically authenticate if needed and append the session token.
     * 
     * @param baseUrl The base URL of the MCP server (e.g., "http://localhost:8080")
     * @param email User's email
     * @param password User's password
     * @param edition PawSQL edition (cloud, enterprise, community)
     * @return The authenticated URL with session token
     * @throws IOException If authentication fails
     * @throws InterruptedException If the request is interrupted
     */
    public static String getAuthenticatedUrl(String baseUrl, String email, String password, String edition) 
            throws IOException, InterruptedException {
        // Normalize the base URL
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        
        // Create a cache key
        String cacheKey = baseUrl + ":" + email;
        
        // Check if we have a valid session in the cache
        SessionInfo sessionInfo = sessionCache.get(cacheKey);
        if (sessionInfo != null && !sessionInfo.isExpired()) {
            log.debug("Using cached session for {}", email);
            return baseUrl + "/api/v1/sse?sessionId=" + sessionInfo.sessionId;
        }
        
        // Need to authenticate
        log.info("Authenticating user {} to MCP server {}", email, baseUrl);
        
        // Build the authentication URL
        String authUrl = baseUrl + "/api/v1/auth";
        
        // Build the request body
        String requestBody = "email=" + email + "&password=" + password + "&edition=" + edition;
        
        // Create the HTTP request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(authUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        
        // Send the request
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        // Check if authentication was successful
        if (response.statusCode() != 200) {
            throw new IOException("Authentication failed: " + response.body());
        }
        
        // Parse the response
        Map<String, Object> responseMap = objectMapper.readValue(response.body(), HashMap.class);
        String sessionId = (String) responseMap.get("sessionId");
        
        if (sessionId == null || sessionId.isEmpty()) {
            throw new IOException("Authentication failed: No session ID returned");
        }
        
        // Cache the session
        sessionCache.put(cacheKey, new SessionInfo(sessionId));
        
        log.info("Successfully authenticated user {} to MCP server {}", email, baseUrl);
        
        // Return the authenticated URL
        return baseUrl + "/api/v1/sse?sessionId=" + sessionId;
    }
    
    /**
     * Clear the session cache for a specific user.
     * 
     * @param baseUrl The base URL of the MCP server
     * @param email User's email
     */
    public static void clearSessionCache(String baseUrl, String email) {
        String cacheKey = baseUrl + ":" + email;
        sessionCache.remove(cacheKey);
    }
    
    /**
     * Clear all session caches.
     */
    public static void clearAllSessionCaches() {
        sessionCache.clear();
    }
    
    /**
     * Session information for caching.
     */
    private static class SessionInfo {
        private final String sessionId;
        private final long creationTime;
        private static final long SESSION_EXPIRY_MS = 23 * 60 * 60 * 1000; // 23 hours
        
        public SessionInfo(String sessionId) {
            this.sessionId = sessionId;
            this.creationTime = System.currentTimeMillis();
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() - creationTime > SESSION_EXPIRY_MS;
        }
    }
}
