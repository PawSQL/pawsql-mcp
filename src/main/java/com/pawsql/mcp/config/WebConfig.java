package com.pawsql.mcp.config;

import com.pawsql.mcp.filter.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Web配置类
 * 用于配置过滤器、拦截器等
 */
@Configuration
public class WebConfig {
    
    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    
    /**
     * 注册JWT认证过滤器
     * 配置过滤器拦截SSE相关请求
     *
     * @return FilterRegistrationBean
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration() {
        FilterRegistrationBean<JwtAuthenticationFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(jwtAuthenticationFilter);
        // 配置拦截的URL模式，包括SSE和MCP端点
        registrationBean.addUrlPatterns("/api/v1/sse", "/api/v1/mcp");
        // 设置过滤器优先级
        registrationBean.setOrder(1);
        return registrationBean;
    }
}
