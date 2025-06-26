package com.pawsql.mcp.util;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.function.Supplier;

/**
 * 请求上下文工具类
 * 用于在非Web请求线程中创建和管理请求上下文
 */
public class RequestContextUtil {

    /**
     * 在模拟请求上下文中执行操作
     * 适用于在非Web请求线程中需要访问请求作用域bean的场景
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
     * 适用于在非Web请求线程中需要访问请求作用域bean的场景
     *
     * @param runnable 要执行的操作
     */
    public static void executeInMockRequestContext(Runnable runnable) {
        executeInMockRequestContext(() -> {
            runnable.run();
            return null;
        });
    }
}
