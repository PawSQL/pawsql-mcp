package com.pawsql.mcp.config;

import com.pawsql.mcp.model.UserSession;
import com.pawsql.mcp.service.AuthenticationService;
import com.pawsql.mcp.service.PawsqlApiService;
import com.pawsql.mcp.service.UserContextManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;

/**
 * Interceptor to set up user context for each request.
 * Extracts authentication information from request headers or parameters
 * and sets up the user context for the request.
 */
@Component
public class UserContextInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(UserContextInterceptor.class);
    
    private final AuthenticationService authService;
    private final UserContextManager userContextManager;
    private final PawsqlApiService apiService;
    
    public UserContextInterceptor(
            AuthenticationService authService,
            UserContextManager userContextManager,
            PawsqlApiService apiService) {
        this.authService = authService;
        this.userContextManager = userContextManager;
        this.apiService = apiService;
    }
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Skip SSE endpoint as it handles authentication itself
        if (request.getRequestURI().endsWith("/sse")) {
            return true;
        }
        
        // Skip authentication endpoint
        if (request.getRequestURI().endsWith("/auth")) {
            return true;
        }
        
        // Extract authentication information from request
        String sessionId = extractSessionId(request);
        String apiKey = extractApiKey(request);
        
        // If no authentication information is provided, allow the request to proceed
        // The individual endpoints will handle authentication as needed
        if ((sessionId == null || sessionId.isEmpty()) && (apiKey == null || apiKey.isEmpty())) {
            log.debug("No authentication information provided for request: {}", request.getRequestURI());
            return true;
        }
        
        // Try to validate session
        Optional<UserSession> userSession;
        
        if (sessionId != null && !sessionId.isEmpty()) {
            userSession = authService.validateSession(sessionId);
        } else {
            userSession = authService.findSessionByApiKey(apiKey);
        }
        
        // If session is valid, set up user context
        if (userSession.isPresent()) {
            UserSession session = userSession.get();
            userContextManager.setCurrentUserSession(session);
            apiService.setCurrentUserSession(session);
            log.debug("Set up user context for request: {}, user: {}", 
                    request.getRequestURI(), session.getEmail());
        } else {
            log.warn("Invalid session or API key for request: {}", request.getRequestURI());
            // Don't block the request, let the endpoint handle authentication
        }
        
        return true;
    }
    
    /**
     * Extract session ID from request.
     * Checks both headers and parameters.
     * 
     * @param request HTTP request
     * @return Session ID if found, null otherwise
     */
    private String extractSessionId(HttpServletRequest request) {
        // Try header first
        String sessionId = request.getHeader("X-Session-ID");
        
        // If not in header, try parameter
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = request.getParameter("sessionId");
        }
        
        return sessionId;
    }
    
    /**
     * Extract API key from request.
     * Checks both headers and parameters.
     * 
     * @param request HTTP request
     * @return API key if found, null otherwise
     */
    private String extractApiKey(HttpServletRequest request) {
        // Try Authorization header first (Bearer token)
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        
        // Try API key header
        String apiKey = request.getHeader("X-API-Key");
        
        // If not in header, try parameter
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = request.getParameter("apiKey");
        }
        
        return apiKey;
    }
}
