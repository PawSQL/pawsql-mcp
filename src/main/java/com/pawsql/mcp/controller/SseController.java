package com.pawsql.mcp.controller;

import com.pawsql.mcp.model.UserSession;
import com.pawsql.mcp.service.*;
import com.pawsql.mcp.service.SqlOptimizeService;
import com.pawsql.mcp.util.ReactorContextPropagator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controller for handling Server-Sent Events (SSE) connections.
 * Provides endpoints for authentication and SSE connections.
 */
@RestController
@RequestMapping("/api/v1")
public class SseController {
    private static final Logger log = LoggerFactory.getLogger(SseController.class);
    
    // Store active SSE emitters with sessionId as key
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    
    private final AuthenticationService authService;
    private final PawsqlApiService apiService;
    private final AuditLogService auditLogService;
    private final PermissionService permissionService;
    private final ThreadLocalUserContext threadLocalUserContext;
    private final SqlOptimizeService sqlOptimizeService;
    private final ReactorContextPropagator reactorContextPropagator;

    public SseController(AuthenticationService authService, PawsqlApiService apiService, 
                       AuditLogService auditLogService, PermissionService permissionService, 
                       ThreadLocalUserContext threadLocalUserContext, SqlOptimizeService sqlOptimizeService,
                       ReactorContextPropagator reactorContextPropagator) {
        this.authService = authService;
        this.apiService = apiService;
        this.auditLogService = auditLogService;
        this.permissionService = permissionService;
        this.threadLocalUserContext = threadLocalUserContext;
        this.sqlOptimizeService = sqlOptimizeService;
        this.reactorContextPropagator = reactorContextPropagator;
    }
    
    /**
     * Authenticate a user and create a new session.
     * 
     * @param email User's email
     * @param password User's password
     * @param edition PawSQL edition (cloud, enterprise, community)
     * @param apiBaseUrl API base URL (required for enterprise edition)
     * @return ResponseEntity containing the session ID if successful, or an error message
     */
    @PostMapping("/auth")
    public ResponseEntity<?> authenticate(
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String edition,
            @RequestParam(required = false) String apiBaseUrl,
            @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        
        String clientIp = forwardedFor != null ? forwardedFor : "unknown";
        userAgent = userAgent != null ? userAgent : "unknown";
        
        var result = authService.authenticateUser(email, password, edition, apiBaseUrl);
        
        if (result.code() == 200) {
            auditLogService.logSecurityEvent("AUTH_SUCCESS", "User authenticated: " + email, clientIp);
            return ResponseEntity.ok(result.data());
        } else {
            auditLogService.logSecurityEvent("AUTH_FAILURE", "Authentication failed for user: " + email, clientIp);
            return ResponseEntity.status(result.code()).body(Map.of("error", result.message()));
        }
    }
    
    /**
     * Establish an SSE connection for a user.
     * The user must be authenticated and provide a valid session ID.
     * 
     * @param sessionId Session ID from authentication
     * @param apiKey API key (alternative authentication method)
     * @return SseEmitter for the SSE connection
     */
    @GetMapping(path = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect(
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String apiKey,
            @RequestHeader(value = "X-Auth-Email", required = false) String email,
            @RequestHeader(value = "X-Auth-Password", required = false) String password,
            @RequestHeader(value = "X-Auth-Edition", required = false) String edition,
            @RequestHeader(value = "X-Auth-ApiBaseUrl", required = false) String apiBaseUrl,
            @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor,
            @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        
        String clientIp = forwardedFor != null ? forwardedFor : "unknown";
        userAgent = userAgent != null ? userAgent : "unknown";
        
        // Validate session or API key or direct credentials
        Optional<UserSession> userSession;
        
        if (sessionId != null && !sessionId.isEmpty()) {
            // Validate by session ID
            userSession = authService.validateSession(sessionId);
        } else if (apiKey != null && !apiKey.isEmpty()) {
            // Validate by API key
            userSession = authService.findSessionByApiKey(apiKey);
        } else if (email != null && !email.isEmpty() && password != null && !password.isEmpty()) {
            // Direct authentication with email and password
            log.info("Attempting direct authentication for SSE connection: {}", email);
            
            // Default edition to 'cloud' if not provided
            String userEdition = (edition != null && !edition.isEmpty()) ? edition : "cloud";
            
            // Authenticate the user
            var authResult = authService.authenticateUser(email, password, userEdition, apiBaseUrl);
            
            if (authResult.code() == 200) {
                // Get the session ID from the authentication result
                @SuppressWarnings("unchecked")
                Map<String, Object> resultData = (Map<String, Object>) authResult.data();
                String newSessionId = (String) resultData.get("sessionId");
                userSession = authService.validateSession(newSessionId);
                
                auditLogService.logSecurityEvent("SSE_DIRECT_AUTH_SUCCESS", 
                        "Direct authentication successful for SSE connection: " + email, clientIp);
            } else {
                // Authentication failed
                log.warn("Direct authentication failed for SSE connection: {}", email);
                auditLogService.logSecurityEvent("SSE_DIRECT_AUTH_FAILURE", 
                        "Direct authentication failed for SSE connection: " + email, clientIp);
                throw new UnauthorizedException("Authentication failed: " + authResult.message());
            }
        } else {
            // No authentication provided
            log.warn("SSE connection attempt without authentication");
            auditLogService.logSecurityEvent("SSE_AUTH_MISSING", "SSE connection attempt without authentication", clientIp);
            throw new UnauthorizedException("Authentication required");
        }
        
        // If session is invalid, throw an exception
        if (userSession.isEmpty()) {
            log.warn("Invalid session or API key for SSE connection");
            auditLogService.logSecurityEvent("SSE_AUTH_INVALID", "Invalid session or API key for SSE connection", clientIp);
            throw new UnauthorizedException("Invalid session or API key");
        }
        
        // Get the user session
        UserSession session = userSession.get();
        
        // Check if user has permission to connect to SSE
        if (!permissionService.hasPermission(session, "sse", "connect", clientIp)) {
            log.warn("User {} does not have permission to connect to SSE", session.getEmail());
            auditLogService.logSecurityEvent("SSE_PERMISSION_DENIED", 
                    "User " + session.getEmail() + " does not have permission to connect to SSE", clientIp);
            throw new UnauthorizedException("You do not have permission to connect to SSE");
        }
        
        // Set the current user session for the API service
        apiService.setCurrentUserSession(session);
        
        // Store the user session in thread-local context for background threads
        threadLocalUserContext.setUserSession(session);
        log.debug("Stored user session in thread-local context: {}", session.getEmail());
        
        // Register this session with all services that need it for background operations
        registerUserSessionForBackgroundOperations(session);
        
        // Create a new SSE emitter
        SseEmitter emitter = new SseEmitter(0L); // No timeout
        
        // Store the emitter
        emitters.put(session.getSessionId(), emitter);
        
        // Add completion callbacks
        emitter.onCompletion(() -> {
            log.info("SSE connection completed for session: {}", session.getSessionId());
            emitters.remove(session.getSessionId());
            auditLogService.logSseEvent(session, "DISCONNECTED", clientIp);
        });
        
        emitter.onTimeout(() -> {
            log.info("SSE connection timed out for session: {}", session.getSessionId());
            emitters.remove(session.getSessionId());
            auditLogService.logSseEvent(session, "TIMEOUT", clientIp);
        });
        
        emitter.onError(e -> {
            log.error("SSE connection error for session: {}", session.getSessionId(), e);
            emitters.remove(session.getSessionId());
            auditLogService.logSseEvent(session, "ERROR", clientIp);
        });
        
        log.info("SSE connection established for user: {}, session: {}", session.getEmail(), session.getSessionId());
        auditLogService.logSseEvent(session, "CONNECTED", clientIp);
        
        // Send a welcome message
        try {
            emitter.send(SseEmitter.event()
                    .name("connect")
                    .data(Map.of(
                            "message", "Connected to PawSQL MCP Server",
                            "email", session.getEmail(),
                            "edition", session.getEdition()
                    ))
            );
        } catch (IOException e) {
            log.error("Error sending welcome message", e);
        }
        
        return emitter;
    }
    
    /**
     * Send an event to a specific user session.
     * 
     * @param sessionId Session ID to send the event to
     * @param eventName Name of the event
     * @param data Data to send
     * @return true if the event was sent successfully, false otherwise
     */
    public boolean sendEvent(String sessionId, String eventName, Object data) {
        // Get the user session for audit logging
        Optional<UserSession> userSession = authService.validateSession(sessionId);
        String userEmail = userSession.map(UserSession::getEmail).orElse("unknown");
        SseEmitter emitter = emitters.get(sessionId);
        
        if (emitter == null) {
            log.warn("No active SSE connection for session: {}", sessionId);
            return false;
        }
        
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(data)
            );
            if (userSession.isPresent()) {
                auditLogService.logSseEvent(userSession.get(), "EVENT_SENT:" + eventName, "system");
            }
            return true;
        } catch (IOException | IllegalStateException e) {
            log.error("Error sending event to session: {}", sessionId, e);
            emitters.remove(sessionId);
            if (userSession.isPresent()) {
                auditLogService.logSseEvent(userSession.get(), "EVENT_ERROR:" + eventName, "system");
            }
            return false;
        }
    }
    
    /**
     * Scheduled task to clean up stale SSE connections.
     * Runs every 60 seconds to check for and remove invalid emitters.
     */
    @Scheduled(fixedRate = 60000) // Run every 60 seconds
    public void cleanupStaleConnections() {
        log.debug("Running scheduled cleanup of stale SSE connections. Current count: {}", emitters.size());
        int removedCount = 0;
        
        for (Map.Entry<String, SseEmitter> entry : emitters.entrySet()) {
            String sessionId = entry.getKey();
            SseEmitter emitter = entry.getValue();
            
            try {
                // Try to send a heartbeat event to check if the connection is still alive
                emitter.send(SseEmitter.event().name("heartbeat").data(""));
            } catch (Exception e) {
                // If sending fails, the connection is stale
                emitters.remove(sessionId);
                log.debug("Removed stale SSE connection for session: {}", sessionId);
                removedCount++;
                
                // Log the event
                Optional<UserSession> userSession = authService.validateSession(sessionId);
                if (userSession.isPresent()) {
                    auditLogService.logSseEvent(userSession.get(), "CONNECTION_CLEANUP", "system");
                }
            }
        }
        
        if (removedCount > 0) {
            log.info("Cleaned up {} stale SSE connections. Remaining: {}", removedCount, emitters.size());
        }
    }
    
    /**
     * Registers the user session with all services that need it for background operations.
     * This ensures that background threads have access to the user context.
     * 
     * @param session The user session to register
     */
    private void registerUserSessionForBackgroundOperations(UserSession session) {
        try {
            // Always set the thread-local user context first
            threadLocalUserContext.setUserSession(session);
            log.debug("Set thread-local user context for session: {} ({})", 
                    session.getSessionId(), session.getEmail());
            
            // Register with the API service
            apiService.setCurrentUserSession(session);
            log.debug("Registered user session with API service: {}", session.getEmail());
            
            // Register with SqlOptimizeService (for tool execution)
            if (sqlOptimizeService != null) {
                // Explicitly set the session in SqlOptimizeService
                // This will store the session in the static session store for background threads
                sqlOptimizeService.setUserSessionForTools(session);
                log.debug("User session registered for SQL optimization tools: {} (session ID: {})", 
                        session.getEmail(), session.getSessionId());
            }
            
            // Create a reactor context with the user session
            // This will be used by reactive components to access the user session
            if (reactorContextPropagator != null) {
                // Create a context with the user session and session ID
                reactorContextPropagator.createContext(session, session.getSessionId());
                log.debug("Created reactor context for user session: {} (session ID: {})", 
                        session.getEmail(), session.getSessionId());
            }
            
            // Register with any other services that need the user session for background operations
            // Add additional services as needed
            
            log.info("Successfully registered user session for background operations: {} (session ID: {})", 
                    session.getEmail(), session.getSessionId());
        } catch (Exception e) {
            log.error("Failed to register user session for background operations: {}", session.getEmail(), e);
        }
    }
    
    /**
     * Exception for unauthorized access.
     */
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException(String message) {
            super(message);
        }
    }
}
