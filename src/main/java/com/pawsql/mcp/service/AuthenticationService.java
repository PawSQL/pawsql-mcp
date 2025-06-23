package com.pawsql.mcp.service;

import com.pawsql.mcp.model.ApiResult;
import com.pawsql.mcp.model.UserSession;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing user authentication and sessions.
 * Handles user login, session creation, validation, and cleanup.
 */
@Service
public class AuthenticationService {
    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);
    
    // Store user sessions with sessionId as key
    private final Map<String, UserSession> sessionsBySessionId = new ConcurrentHashMap<>();
    
    // Store user sessions with apiKey as key for quick lookup
    private final Map<String, UserSession> sessionsByApiKey = new ConcurrentHashMap<>();
    
    // PawSQL API service for user authentication
    private final PawsqlApiService apiService;
    
    // Audit log service
    private final AuditLogService auditLogService;
    
    // Session timeout in hours, configurable via application.properties
    @Value("${pawsql.session.timeout.hours:24}")
    private int sessionTimeoutHours;
    
    // Cleanup interval in minutes, configurable via application.properties
    @Value("${pawsql.session.cleanup.interval.minutes:60}")
    private int cleanupIntervalMinutes;
    
    // Maximum number of sessions per user, configurable via application.properties
    @Value("${pawsql.session.max.per.user:5}")
    private int maxSessionsPerUser;
    
    // Scheduled executor for session cleanup
    private final ScheduledExecutorService scheduledExecutor;

    public AuthenticationService(PawsqlApiService apiService, AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
        this.apiService = apiService;
        
        // Initialize the scheduled executor
        this.scheduledExecutor = new ScheduledThreadPoolExecutor(1);
        
        log.info("AuthenticationService initialized");
    }
    
    /**
     * Initialize the service after all properties have been set.
     * This ensures that @Value annotations are processed before using their values.
     */
    @PostConstruct
    public void init() {
        // Start a background thread to periodically clean up expired sessions
        startSessionCleanupThread();
        
        log.info("AuthenticationService fully initialized with session timeout: {} hours, cleanup interval: {} minutes", 
                sessionTimeoutHours, cleanupIntervalMinutes);
    }
    
    /**
     * Shutdown hook to clean up resources when the application is shutting down.
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down AuthenticationService");
        scheduledExecutor.shutdown();
        try {
            if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduledExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("AuthenticationService shutdown complete");
    }

    /**
     * Authenticate a user with email and password, and create a new session.
     * 
     * @param email User's email
     * @param password User's password
     * @param edition PawSQL edition (cloud, enterprise, community)
     * @param apiBaseUrl API base URL (required for enterprise edition)
     * @return ApiResult containing the session ID if successful, or an error message
     */
    public ApiResult authenticateUser(String email, String password, String edition, String apiBaseUrl) {
        String ipAddress = getClientIpAddress();
        String userAgent = getClientUserAgent();
        try {
            log.info("Authenticating user: {}, edition: {}", email, edition);
            
            // Validate required parameters
            if (email == null || email.isEmpty()) {
                return new ApiResult(400, "Email is required", null);
            }
            if (password == null || password.isEmpty()) {
                return new ApiResult(400, "Password is required", null);
            }
            if (edition == null || edition.isEmpty()) {
                return new ApiResult(400, "Edition is required", null);
            }
            
            // API base URL is required for all editions in multi-user mode
            if (apiBaseUrl == null || apiBaseUrl.isEmpty()) {
                return new ApiResult(400, "API base URL is required", null);
            }
            
            // Get API key from PawSQL API
            String apiKey = apiService.getApiKey(email, password, edition, apiBaseUrl);
            if (apiKey == null || apiKey.isEmpty()) {
                return new ApiResult(401, "Authentication failed", null);
            }
            
            // Check if user already has a session
            Optional<UserSession> existingSession = findSessionByApiKey(apiKey);
            if (existingSession.isPresent()) {
                // Update last accessed time and return existing session
                UserSession session = existingSession.get();
                session.updateLastAccessedAt();
                log.info("User {} authenticated with existing session: {}", email, session.getSessionId());
                auditLogService.logAuthentication(email, true, session.getSessionId(), ipAddress, userAgent);
                auditLogService.logSessionEvent(session, "VALIDATED", ipAddress);
                return new ApiResult(200, "Authentication successful", Map.of(
                        "sessionId", session.getSessionId(),
                        "email", session.getEmail(),
                        "edition", session.getEdition()));
            }
            
            // Check if user has reached maximum number of sessions
            long userSessionCount = sessionsBySessionId.values().stream()
                    .filter(s -> s.getEmail().equals(email))
                    .count();
                    
            if (userSessionCount >= maxSessionsPerUser) {
                log.warn("User {} has reached maximum number of sessions: {}", email, maxSessionsPerUser);
                // Remove oldest session for this user
                sessionsBySessionId.values().stream()
                        .filter(s -> s.getEmail().equals(email))
                        .min((s1, s2) -> s1.getLastAccessedAt().compareTo(s2.getLastAccessedAt()))
                        .ifPresent(this::removeSession);
            }
            
            // Create a new session
            UserSession newSession = new UserSession(apiKey, email, edition, apiBaseUrl);
            sessionsBySessionId.put(newSession.getSessionId(), newSession);
            sessionsByApiKey.put(apiKey, newSession);
            
            log.info("User authenticated successfully: {}, session: {}", email, newSession.getSessionId());
            auditLogService.logAuthentication(email, true, newSession.getSessionId(), ipAddress, userAgent);
            auditLogService.logSessionEvent(newSession, "CREATED", ipAddress);
            return new ApiResult(200, "Authentication successful", Map.of(
                    "sessionId", newSession.getSessionId(),
                    "email", newSession.getEmail(),
                    "edition", newSession.getEdition()));
        } catch (Exception e) {
            log.error("Authentication failed", e);
            auditLogService.logAuthentication(email, false, null, ipAddress, userAgent);
            auditLogService.logSecurityEvent("AUTH_FAILURE", "Authentication failed for user: " + email, ipAddress);
            return new ApiResult(500, "Authentication failed: " + e.getMessage(), null);
        }
    }

    /**
     * Validate a session by session ID.
     * 
     * @param sessionId Session ID to validate
     * @return Optional containing the user session if valid, or empty if invalid
     */
    public Optional<UserSession> validateSession(String sessionId) {
        String ipAddress = getClientIpAddress();
        if (sessionId == null || sessionId.isEmpty()) {
            return Optional.empty();
        }
        
        UserSession session = sessionsBySessionId.get(sessionId);
        if (session == null) {
            return Optional.empty();
        }
        
        // Check if session has expired
        if (isSessionExpired(session)) {
            log.info("Session expired: {}", sessionId);
            auditLogService.logSessionEvent(session, "EXPIRED", ipAddress);
            removeSession(session);
            return Optional.empty();
        }
        
        // Update last accessed time
        session.updateLastAccessedAt();
        auditLogService.logSessionEvent(session, "VALIDATED", ipAddress);
        return Optional.of(session);
    }
    
    /**
     * Find a session by API key.
     * 
     * @param apiKey API key to look up
     * @return Optional containing the user session if found, or empty if not found
     */
    public Optional<UserSession> findSessionByApiKey(String apiKey) {
        String ipAddress = getClientIpAddress();
        if (apiKey == null || apiKey.isEmpty()) {
            return Optional.empty();
        }
        
        UserSession session = sessionsByApiKey.get(apiKey);
        if (session == null) {
            return Optional.empty();
        }
        
        // Check if session has expired
        if (isSessionExpired(session)) {
            log.info("Session expired for API key: {}", apiKey);
            auditLogService.logSessionEvent(session, "EXPIRED", ipAddress);
            removeSession(session);
            return Optional.empty();
        }
        
        return Optional.of(session);
    }
    
    /**
     * Remove a session.
     * 
     * @param session Session to remove
     */
    public void removeSession(UserSession session) {
        sessionsBySessionId.remove(session.getSessionId());
        sessionsByApiKey.remove(session.getApiKey());
        log.info("Session removed: {}", session.getSessionId());
        auditLogService.logSessionEvent(session, "REMOVED", getClientIpAddress());
    }
    
    /**
     * Check if a session has expired.
     * 
     * @param session Session to check
     * @return true if the session has expired, false otherwise
     */
    private boolean isSessionExpired(UserSession session) {
        Instant now = Instant.now();
        Duration sinceLastAccessed = Duration.between(session.getLastAccessedAt(), now);
        Duration timeout = Duration.ofHours(sessionTimeoutHours);
        return sinceLastAccessed.compareTo(timeout) > 0;
    }
    
    /**
     * Start a scheduled task to periodically clean up expired sessions.
     */
    private void startSessionCleanupThread() {
        scheduledExecutor.scheduleAtFixedRate(
                this::cleanupExpiredSessions,
                cleanupIntervalMinutes,
                cleanupIntervalMinutes,
                TimeUnit.MINUTES
        );
        
        log.info("Session cleanup task scheduled to run every {} minutes", cleanupIntervalMinutes);
    }
    
    /**
     * Clean up expired sessions.
     */
    private void cleanupExpiredSessions() {
        try {
            log.info("Cleaning up expired sessions");
            Instant now = Instant.now();
            Duration timeout = Duration.ofHours(sessionTimeoutHours);
            
            // Find expired sessions
            List<UserSession> expiredSessions = new ArrayList<>();
            sessionsBySessionId.values().forEach(session -> {
                if (Duration.between(session.getLastAccessedAt(), now).compareTo(timeout) > 0) {
                    expiredSessions.add(session);
                }
            });
            
            // Remove expired sessions
            for (UserSession session : expiredSessions) {
                log.info("Expired session removed: {}, user: {}, last accessed: {}", 
                        session.getSessionId(), session.getEmail(), session.getLastAccessedAt());
                auditLogService.logSessionEvent(session, "EXPIRED_AUTO_CLEANUP", "system");
                sessionsBySessionId.remove(session.getSessionId());
                sessionsByApiKey.remove(session.getApiKey());
            }
            
            log.info("Session cleanup completed, removed {} expired sessions, active sessions: {}", 
                    expiredSessions.size(), sessionsBySessionId.size());
        } catch (Exception e) {
            log.error("Error during session cleanup", e);
        }
    }
    
    /**
     * Get the current number of active sessions.
     * 
     * @return The number of active sessions
     */
    public int getActiveSessionCount() {
        return sessionsBySessionId.size();
    }
    
    /**
     * Get the session timeout in hours.
     * 
     * @return The session timeout in hours
     */
    public int getSessionTimeoutHours() {
        return sessionTimeoutHours;
    }
    
    /**
     * Get the client IP address from the current request.
     * 
     * @return The client IP address, or "unknown" if not available
     */
    private String getClientIpAddress() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                String ipAddress = attributes.getRequest().getHeader("X-Forwarded-For");
                if (ipAddress == null || ipAddress.isEmpty()) {
                    ipAddress = attributes.getRequest().getRemoteAddr();
                }
                return ipAddress;
            }
        } catch (Exception e) {
            log.debug("Could not get client IP address", e);
        }
        return "unknown";
    }
    
    /**
     * Get the client user agent from the current request.
     * 
     * @return The client user agent, or "unknown" if not available
     */
    private String getClientUserAgent() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                return attributes.getRequest().getHeader("User-Agent");
            }
        } catch (Exception e) {
            log.debug("Could not get client user agent", e);
        }
        return "unknown";
    }
}
