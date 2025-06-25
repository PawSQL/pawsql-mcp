package com.pawsql.mcp.service;

import com.pawsql.mcp.model.UserSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Manages the current user context within a request scope.
 * This ensures that each request has access to the current user's session information,
 * allowing tools and services to operate in the correct user context.
 */
@Component
@RequestScope
public class UserContextManager {
    private static final Logger log = LoggerFactory.getLogger(UserContextManager.class);
    
    private UserSession currentUserSession;
    
    /**
     * Set the current user session for this request.
     * 
     * @param userSession The user session to set as current
     */
    public void setCurrentUserSession(UserSession userSession) {
        if (userSession == null) {
            throw new IllegalArgumentException("User session cannot be null");
        }
        
        this.currentUserSession = userSession;
        log.debug("Set current user session: {}", userSession.getEmail());
    }
    
    /**
     * Get the current user session.
     * 
     * @return The current user session, or null if not set
     */
    public UserSession getCurrentUserSession() {
        return currentUserSession;
    }
    
    /**
     * Check if a user session is set.
     * 
     * @return true if a user session is set, false otherwise
     */
    public boolean hasUserSession() {
        return currentUserSession != null;
    }
    
    /**
     * Get the current user's API key.
     * 
     * @return The current user's API key, or null if not set
     * @throws IllegalStateException if no user session is set
     */
    public String getCurrentApiKey() {
        if (currentUserSession == null) {
            throw new IllegalStateException("No user session is set");
        }
        
        return currentUserSession.getApiKey();
    }
    
    /**
     * Get the current user's email.
     * 
     * @return The current user's email, or null if not set
     * @throws IllegalStateException if no user session is set
     */
    public String getCurrentUserEmail() {
        if (currentUserSession == null) {
            throw new IllegalStateException("No user session is set");
        }
        
        return currentUserSession.getEmail();
    }
    
    /**
     * Get the current user's session ID.
     * 
     * @return The current user's session ID, or null if not set
     * @throws IllegalStateException if no user session is set
     */
    public String getCurrentSessionId() {
        if (currentUserSession == null) {
            throw new IllegalStateException("No user session is set");
        }
        
        return currentUserSession.getSessionId();
    }
}
