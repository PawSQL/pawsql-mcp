package com.pawsql.mcp.service;

import com.pawsql.mcp.model.UserSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Thread-local storage for user sessions to handle background threads.
 * This allows services to access user context in non-request threads.
 */
@Component
public class ThreadLocalUserContext {
    private static final Logger log = LoggerFactory.getLogger(ThreadLocalUserContext.class);
    
    private static final ThreadLocal<UserSession> threadLocalUserSession = new ThreadLocal<>();
    
    /**
     * Set the user session for the current thread.
     * This allows background threads to access the user session.
     * 
     * @param session The user session to set
     */
    public void setUserSession(UserSession session) {
        if (session == null) {
            log.warn("Attempt to set null user session in thread-local storage");
            return;
        }
        
        threadLocalUserSession.set(session);
        log.debug("Set thread-local user session: {}", session.getEmail());
    }
    
    /**
     * Get the current user session from thread-local storage.
     * 
     * @return The current user session, or null if not set
     */
    public UserSession getUserSession() {
        return threadLocalUserSession.get();
    }
    
    /**
     * Check if a user session is set in thread-local storage.
     * 
     * @return true if a user session is set, false otherwise
     */
    public boolean hasUserSession() {
        return threadLocalUserSession.get() != null;
    }
    
    /**
     * Clear the thread-local user session.
     * Should be called when the thread is done to prevent memory leaks.
     */
    public void clearUserSession() {
        threadLocalUserSession.remove();
        log.debug("Cleared thread-local user session");
    }
}
