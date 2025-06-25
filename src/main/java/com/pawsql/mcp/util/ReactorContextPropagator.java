package com.pawsql.mcp.util;

import com.pawsql.mcp.model.UserSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.function.Function;

/**
 * Utility class for propagating user context in reactive streams.
 * This helps ensure user context is available in background/reactive threads.
 */
@Component
public class ReactorContextPropagator {
    private static final Logger log = LoggerFactory.getLogger(ReactorContextPropagator.class);
    private static final String USER_SESSION_KEY = "USER_SESSION";
    private static final String SESSION_ID_KEY = "SESSION_ID";

    /**
     * Creates a context with the given user session.
     *
     * @param userSession The user session to add to the context
     * @param sessionId The MCP session ID
     * @return A reactor Context with the user session
     */
    public Context createContext(UserSession userSession, String sessionId) {
        if (userSession == null) {
            log.warn("Attempting to create context with null user session");
            return Context.empty();
        }
        
        log.debug("Creating reactor context with session ID: {}", sessionId);
        return Context.of(USER_SESSION_KEY, userSession, SESSION_ID_KEY, sessionId);
    }

    /**
     * Extracts the user session from the current reactor context.
     *
     * @return A Mono that will emit the user session if available
     */
    public Mono<UserSession> getUserSession() {
        return Mono.deferContextual(contextView -> {
            if (contextView.hasKey(USER_SESSION_KEY)) {
                UserSession session = contextView.get(USER_SESSION_KEY);
                log.debug("Retrieved user session from reactor context: {}", 
                    session != null ? session.getSessionId() : "null");
                return Mono.justOrEmpty(session);
            }
            log.debug("No user session found in reactor context");
            return Mono.empty();
        });
    }
    
    /**
     * Extracts the MCP session ID from the current reactor context.
     *
     * @return A Mono that will emit the session ID if available
     */
    public Mono<String> getSessionId() {
        return Mono.deferContextual(contextView -> {
            if (contextView.hasKey(SESSION_ID_KEY)) {
                String sessionId = contextView.get(SESSION_ID_KEY);
                log.debug("Retrieved session ID from reactor context: {}", sessionId);
                return Mono.justOrEmpty(sessionId);
            }
            log.debug("No session ID found in reactor context");
            return Mono.empty();
        });
    }
    
    /**
     * Creates a function that can be used with Mono.transformDeferredContextual()
     * to propagate the user context to downstream operators.
     *
     * @param <T> The type of the Mono
     * @return A function that propagates context
     */
    public <T> Function<Mono<T>, Mono<T>> propagateContext() {
        return mono -> mono.flatMap(value -> 
            getUserSession()
                .doOnNext(session -> log.debug("Propagating user session: {}", 
                    session != null ? session.getSessionId() : "null"))
                .map(session -> value)
                .switchIfEmpty(Mono.just(value))
        );
    }
}
