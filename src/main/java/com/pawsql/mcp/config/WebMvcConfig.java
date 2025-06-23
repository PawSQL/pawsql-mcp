package com.pawsql.mcp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC configuration for registering interceptors.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    
    private final UserContextInterceptor userContextInterceptor;
    
    public WebMvcConfig(UserContextInterceptor userContextInterceptor) {
        this.userContextInterceptor = userContextInterceptor;
    }
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Register the user context interceptor for all requests
        registry.addInterceptor(userContextInterceptor);
    }
}
