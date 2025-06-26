package com.pawsql.mcp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextListener;
import org.springframework.web.filter.RequestContextFilter;

/**
 * 请求上下文配置
 * 确保在所有线程中都可以访问请求作用域
 */
@Configuration
public class RequestContextConfig {

    /**
     * 注册RequestContextFilter以确保请求上下文在所有线程中可用
     * 这对于在非Web请求线程中访问请求作用域的bean是必要的
     */
    @Bean
    public RequestContextFilter requestContextFilter() {
        return new RequestContextFilter();
    }

    /**
     * 注册RequestContextListener以确保请求上下文在所有线程中可用
     * 这是另一种确保请求上下文可用的方式
     */
    @Bean
    public RequestContextListener requestContextListener() {
        return new RequestContextListener();
    }
}
