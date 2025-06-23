package com.pawsql.mcp.service;

import com.pawsql.mcp.model.UserSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for audit logging of user actions and system events.
 * Provides methods for logging authentication, API calls, and other security-relevant events.
 */
@Service
public class AuditLogService {
    private static final Logger log = LoggerFactory.getLogger("AUDIT");
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    
    /**
     * Log a user authentication event.
     * 
     * @param email User's email
     * @param success Whether authentication was successful
     * @param sessionId Session ID if successful, null otherwise
     * @param ipAddress IP address of the client
     * @param userAgent User agent of the client
     */
    public void logAuthentication(String email, boolean success, String sessionId, String ipAddress, String userAgent) {
        Map<String, Object> details = new HashMap<>();
        details.put("email", email);
        details.put("success", success);
        details.put("sessionId", sessionId);
        details.put("ipAddress", ipAddress);
        details.put("userAgent", userAgent);
        
        logEvent("AUTHENTICATION", details);
    }
    
    /**
     * Log a session event (creation, validation, expiration, etc.).
     * 
     * @param session User session
     * @param eventType Type of event (CREATED, VALIDATED, EXPIRED, etc.)
     * @param ipAddress IP address of the client
     */
    public void logSessionEvent(UserSession session, String eventType, String ipAddress) {
        Map<String, Object> details = new HashMap<>();
        details.put("sessionId", session.getSessionId());
        details.put("email", session.getEmail());
        details.put("edition", session.getEdition());
        details.put("ipAddress", ipAddress);
        details.put("eventType", eventType);
        
        logEvent("SESSION", details);
    }
    
    /**
     * Log an API call event.
     * 
     * @param session User session
     * @param endpoint API endpoint
     * @param method HTTP method
     * @param statusCode HTTP status code
     * @param ipAddress IP address of the client
     */
    public void logApiCall(UserSession session, String endpoint, String method, int statusCode, String ipAddress) {
        Map<String, Object> details = new HashMap<>();
        details.put("sessionId", session != null ? session.getSessionId() : "anonymous");
        details.put("email", session != null ? session.getEmail() : "anonymous");
        details.put("endpoint", endpoint);
        details.put("method", method);
        details.put("statusCode", statusCode);
        details.put("ipAddress", ipAddress);
        
        logEvent("API_CALL", details);
    }
    
    /**
     * Log an SSE connection event.
     * 
     * @param session User session
     * @param eventType Type of event (CONNECTED, DISCONNECTED, etc.)
     * @param ipAddress IP address of the client
     */
    public void logSseEvent(UserSession session, String eventType, String ipAddress) {
        Map<String, Object> details = new HashMap<>();
        details.put("sessionId", session.getSessionId());
        details.put("email", session.getEmail());
        details.put("eventType", eventType);
        details.put("ipAddress", ipAddress);
        
        logEvent("SSE", details);
    }
    
    /**
     * Log a permission check event.
     * 
     * @param session User session
     * @param resource Resource being accessed
     * @param permission Permission being checked
     * @param granted Whether permission was granted
     * @param ipAddress IP address of the client
     */
    public void logPermissionCheck(UserSession session, String resource, String permission, boolean granted, String ipAddress) {
        Map<String, Object> details = new HashMap<>();
        details.put("sessionId", session.getSessionId());
        details.put("email", session.getEmail());
        details.put("resource", resource);
        details.put("permission", permission);
        details.put("granted", granted);
        details.put("ipAddress", ipAddress);
        
        logEvent("PERMISSION", details);
    }
    
    /**
     * Log a security event.
     * 
     * @param eventType Type of event
     * @param message Event message
     * @param ipAddress IP address of the client
     */
    public void logSecurityEvent(String eventType, String message, String ipAddress) {
        Map<String, Object> details = new HashMap<>();
        details.put("eventType", eventType);
        details.put("message", message);
        details.put("ipAddress", ipAddress);
        
        logEvent("SECURITY", details);
    }
    
    /**
     * Log a generic event.
     * 
     * @param category Event category
     * @param details Event details
     */
    private void logEvent(String category, Map<String, Object> details) {
        String timestamp = formatter.format(Instant.now());
        
        StringBuilder builder = new StringBuilder();
        builder.append("[").append(timestamp).append("] ");
        builder.append("[").append(category).append("] ");
        
        for (Map.Entry<String, Object> entry : details.entrySet()) {
            if (entry.getValue() != null) {
                builder.append(entry.getKey()).append("=").append(entry.getValue()).append(" ");
            }
        }
        
        log.info(builder.toString());
    }
}
