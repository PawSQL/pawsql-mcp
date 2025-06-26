package com.pawsql.mcp.util;

import com.pawsql.mcp.context.RequestContextManager;
import com.pawsql.mcp.model.JwtTokenPayload;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.function.Supplier;

/**
 * 请求上下文工具类
 * 用于在非Web请求线程中创建和管理请求上下文
 * 支持在非Web线程中设置和传递认证信息
 */
public class RequestContextUtil {

    /**
     * 在模拟请求上下文中执行操作
     * 适用于在非Web请求线程中需要访问请求作用域的bean的场景
     * 注意：此方法仅创建请求上下文，不会设置认证信息
     *
     * @param supplier 要执行的操作
     * @param <T> 返回值类型
     * @return 操作的返回值
     */
    public static <T> T executeInMockRequestContext(Supplier<T> supplier) {
        // 保存当前请求上下文（如果有）
        RequestAttributes existingAttributes = RequestContextHolder.getRequestAttributes();
        
        try {
            // 如果当前没有请求上下文，创建一个模拟的请求上下文
            if (existingAttributes == null) {
                MockHttpServletRequest mockRequest = new MockHttpServletRequest();
                ServletRequestAttributes servletRequestAttributes = new ServletRequestAttributes(mockRequest);
                RequestContextHolder.setRequestAttributes(servletRequestAttributes);
            }
            
            // 在请求上下文中执行操作
            return supplier.get();
        } finally {
            // 恢复原始请求上下文
            RequestContextHolder.setRequestAttributes(existingAttributes);
        }
    }
    
    /**
     * 在模拟请求上下文中执行无返回值的操作
     * 适用于在非Web请求线程中需要访问请求作用域的bean的场景
     * 注意：此方法仅创建请求上下文，不会设置认证信息
     *
     * @param runnable 要执行的操作
     */
    public static void executeInMockRequestContext(Runnable runnable) {
        executeInMockRequestContext(() -> {
            runnable.run();
            return null;
        });
    }
    
    /**
     * 在模拟请求上下文中执行操作，并设置认证信息
     * 适用于在非Web线程中需要使用认证信息的场景
     *
     * @param tokenPayload 认证信息
     * @param contextManager 请求上下文管理器
     * @param supplier 要执行的操作
     * @param <T> 返回值类型
     * @return 操作的返回值
     */
    public static <T> T executeWithAuth(JwtTokenPayload tokenPayload, RequestContextManager contextManager, Supplier<T> supplier) {
        return executeInMockRequestContext(() -> {
            // 保存原有认证信息
            JwtTokenPayload originalPayload = contextManager.getTokenPayload();
            try {
                // 设置新的认证信息
                if (tokenPayload != null) {
                    contextManager.setTokenPayload(tokenPayload);
                } else {
                    contextManager.clear();
                }
                
                // 执行操作
                return supplier.get();
            } finally {
                // 恢复原有认证信息
                if (originalPayload != null) {
                    contextManager.setTokenPayload(originalPayload);
                } else {
                    contextManager.clear();
                }
            }
        });
    }
    
    /**
     * 在模拟请求上下文中执行无返回值的操作，并设置认证信息
     * 适用于在非Web线程中需要使用认证信息的场景
     *
     * @param tokenPayload 认证信息
     * @param contextManager 请求上下文管理器
     * @param runnable 要执行的操作
     */
    public static void executeWithAuth(JwtTokenPayload tokenPayload, RequestContextManager contextManager, Runnable runnable) {
        executeWithAuth(tokenPayload, contextManager, () -> {
            runnable.run();
            return null;
        });
    }
}
