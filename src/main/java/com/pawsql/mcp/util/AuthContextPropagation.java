package com.pawsql.mcp.util;

import com.pawsql.mcp.context.RequestContextManager;
import com.pawsql.mcp.model.JwtTokenPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskDecorator;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * 认证上下文传播工具类
 * 用于在异步任务和非Web线程中传递认证信息
 */
public class AuthContextPropagation {
    private static final Logger log = LoggerFactory.getLogger(AuthContextPropagation.class);
    
    /**
     * 创建一个包装了认证上下文的Runnable
     * 在新线程中执行时，会自动传递当前线程的认证信息
     *
     * @param requestContextManager 请求上下文管理器
     * @param runnable 要执行的任务
     * @return 包装后的Runnable
     */
    public static Runnable withAuthContext(RequestContextManager requestContextManager, Runnable runnable) {
        // 获取当前线程的认证信息
        JwtTokenPayload currentPayload = requestContextManager.getTokenPayload();
        
        return () -> {
            try {
                // 在新线程中设置认证信息
                if (currentPayload != null) {
                    requestContextManager.setTokenPayload(currentPayload);
                    log.debug("已将认证信息传递到新线程[{}]", Thread.currentThread().getName());
                }
                
                // 执行原始任务
                runnable.run();
            } finally {
                // 清理认证信息，避免内存泄漏
                requestContextManager.clear();
            }
        };
    }
    
    /**
     * 创建一个包装了认证上下文的Callable
     * 在新线程中执行时，会自动传递当前线程的认证信息
     *
     * @param requestContextManager 请求上下文管理器
     * @param callable 要执行的任务
     * @param <V> 返回值类型
     * @return 包装后的Callable
     */
    public static <V> Callable<V> withAuthContext(RequestContextManager requestContextManager, Callable<V> callable) {
        // 获取当前线程的认证信息
        JwtTokenPayload currentPayload = requestContextManager.getTokenPayload();
        
        return () -> {
            try {
                // 在新线程中设置认证信息
                if (currentPayload != null) {
                    requestContextManager.setTokenPayload(currentPayload);
                    log.debug("已将认证信息传递到新线程[{}]", Thread.currentThread().getName());
                }
                
                // 执行原始任务
                return callable.call();
            } finally {
                // 清理认证信息，避免内存泄漏
                requestContextManager.clear();
            }
        };
    }
    
    /**
     * 创建一个包装了认证上下文的Supplier
     * 在新线程中执行时，会自动传递当前线程的认证信息
     *
     * @param requestContextManager 请求上下文管理器
     * @param supplier 要执行的任务
     * @param <T> 返回值类型
     * @return 包装后的Supplier
     */
    public static <T> Supplier<T> withAuthContext(RequestContextManager requestContextManager, Supplier<T> supplier) {
        // 获取当前线程的认证信息
        JwtTokenPayload currentPayload = requestContextManager.getTokenPayload();
        
        return () -> {
            try {
                // 在新线程中设置认证信息
                if (currentPayload != null) {
                    requestContextManager.setTokenPayload(currentPayload);
                    log.debug("已将认证信息传递到新线程[{}]", Thread.currentThread().getName());
                }
                
                // 执行原始任务
                return supplier.get();
            } finally {
                // 清理认证信息，避免内存泄漏
                requestContextManager.clear();
            }
        };
    }
    
    /**
     * Spring异步任务装饰器
     * 用于在Spring的@Async注解的方法中传递认证信息
     */
    public static class AuthContextTaskDecorator implements TaskDecorator {
        private final RequestContextManager requestContextManager;
        
        public AuthContextTaskDecorator(RequestContextManager requestContextManager) {
            this.requestContextManager = requestContextManager;
        }
        
        @Override
        public Runnable decorate(Runnable runnable) {
            return withAuthContext(requestContextManager, runnable);
        }
    }
}
