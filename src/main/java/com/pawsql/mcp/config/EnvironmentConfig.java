package com.pawsql.mcp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Configuration class for handling environment variables.
 * This class provides default values for environment variables
 * and fallback mechanisms for the multi-user context.
 */
@Configuration
public class EnvironmentConfig {
    private static final Logger log = LoggerFactory.getLogger(EnvironmentConfig.class);
    
    private final Environment environment;
    
    public EnvironmentConfig(Environment environment) {
        this.environment = environment;
    }
    
    /**
     * Get the API base URL from environment variables.
     * In multi-user mode, this should be null and provided by each user session.
     * 
     * @return The API base URL from environment, or null if not set
     */
    @Bean
    public String defaultApiBaseUrl() {
        String apiBaseUrl = getEnvVar("PAWSQL_API_BASE_URL");
        if (apiBaseUrl != null) {
            log.warn("API base URL is set from environment. In multi-user mode, this should be provided by each user session.");
        }
        return apiBaseUrl;
    }
    
    /**
     * Get the PawSQL edition from environment variables.
     * In multi-user mode, this should be null and provided by each user session.
     * 
     * @return The PawSQL edition from environment, or null if not set
     */
    @Bean
    public String defaultEdition() {
        String edition = getEnvVar("PAWSQL_EDITION");
        if (edition != null) {
            log.warn("Edition is set from environment. In multi-user mode, this should be provided by each user session.");
        }
        return edition;
    }
    
    /**
     * Get the default API email from environment variables.
     * This is used for fallback authentication when no user context is available.
     * 
     * @return The default API email, or null if not set
     */
    @Bean
    public String defaultApiEmail() {
        String apiEmail = getEnvVar("PAWSQL_API_EMAIL");
        if (apiEmail != null) {
            log.info("Default API email is set from environment");
        }
        return apiEmail;
    }
    
    /**
     * Get the default API password from environment variables.
     * This is used for fallback authentication when no user context is available.
     * 
     * @return The default API password, or null if not set
     */
    @Bean
    public String defaultApiPassword() {
        String apiPassword = getEnvVar("PAWSQL_API_PASSWORD");
        if (apiPassword != null) {
            log.info("Default API password is set from environment");
        }
        return apiPassword;
    }
    
    /**
     * Get an environment variable value.
     * 
     * @param name The name of the environment variable
     * @return The value of the environment variable, or null if not set
     */
    private String getEnvVar(String name) {
        String value = environment.getProperty(name);
        if (value == null || value.isEmpty()) {
            value = System.getenv(name);
        }
        return (value != null && !value.isEmpty()) ? value : null;
    }
}
