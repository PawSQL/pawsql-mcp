package com.pawsql.mcp.config;

import com.pawsql.mcp.context.RequestContextManager;
import com.pawsql.mcp.util.AuthContextPropagation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步任务配置
 * 配置异步任务执行器，支持认证信息的自动传播
 */
@Configuration
@EnableAsync
public class AsyncConfig {
    
    @Autowired
    private RequestContextManager requestContextManager;
    
    /**
     * 配置异步任务执行器
     * 使用AuthContextTaskDecorator装饰任务，自动传递认证信息
     *
     * @return 异步任务执行器
     */
    @Bean("taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("pawsql-async-");
        
        // 设置任务装饰器，自动传递认证信息
        executor.setTaskDecorator(new AuthContextPropagation.AuthContextTaskDecorator(requestContextManager));
        
        executor.initialize();
        return executor;
    }
}
