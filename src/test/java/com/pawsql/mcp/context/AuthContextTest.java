package com.pawsql.mcp.context;

import com.pawsql.mcp.model.JwtTokenPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 认证上下文测试
 */
public class AuthContextTest {

    private RequestContextManager contextManager;
    private JwtTokenPayload testPayload;
    private AuthContext authContext;

    @BeforeEach
    public void setUp() {
        contextManager = new RequestContextManager();
        testPayload = new JwtTokenPayload(
                "https://test-api.pawsql.com",
                "https://test-api.pawsql.com",
                "v1",
                "testuser",
                "test-api-key-12345"
        );
        authContext = new AuthContext(testPayload);
    }

    @Test
    public void testGetters() {
        // 验证各个字段
        assertEquals("https://test-api.pawsql.com", authContext.getBaseUrl());
        assertEquals("https://test-api.pawsql.com", authContext.getFrontendUrl());
        assertEquals("v1", authContext.getEdition());
        assertEquals("testuser", authContext.getUsername());
        assertEquals("test-api-key-12345", authContext.getApiKey());
        assertTrue(authContext.isAuthenticated());
    }

    @Test
    public void testNullPayload() {
        // 创建空的认证上下文
        AuthContext emptyContext = new AuthContext(null);
        
        // 验证各个字段为null
        assertNull(emptyContext.getBaseUrl());
        assertNull(emptyContext.getFrontendUrl());
        assertNull(emptyContext.getEdition());
        assertNull(emptyContext.getUsername());
        assertNull(emptyContext.getApiKey());
        assertFalse(emptyContext.isAuthenticated());
    }

    @Test
    public void testFromRequestContext() {
        // 设置请求上下文
        contextManager.setTokenPayload(testPayload);
        
        // 从请求上下文创建认证上下文
        AuthContext context = AuthContext.fromRequestContext(contextManager);
        
        // 验证认证上下文
        assertNotNull(context);
        assertEquals(testPayload.getBaseUrl(), context.getBaseUrl());
        assertEquals(testPayload.getEdition(), context.getEdition());
        assertEquals(testPayload.getUsername(), context.getUsername());
        assertEquals(testPayload.getApiKey(), context.getApiKey());
    }

    @Test
    public void testFromEmptyRequestContext() {
        // 确保请求上下文为空
        contextManager.clear();
        
        // 从空的请求上下文创建认证上下文
        AuthContext context = AuthContext.fromRequestContext(contextManager);
        
        // 验证认证上下文为null
        assertNull(context);
    }

    @Test
    public void testApplyToRequestContext() {
        // 确保请求上下文为空
        contextManager.clear();
        
        // 将认证上下文应用到请求上下文
        authContext.applyToRequestContext(contextManager);
        
        // 验证请求上下文已设置
        assertTrue(contextManager.isAuthenticated());
        assertEquals(testPayload.getBaseUrl(), contextManager.getBaseUrl());
        assertEquals(testPayload.getEdition(), contextManager.getVersion());
        assertEquals(testPayload.getUsername(), contextManager.getUsername());
        assertEquals(testPayload.getApiKey(), contextManager.getApiKey());
    }

    @Test
    public void testApplyNullToRequestContext() {
        // 先设置请求上下文
        contextManager.setTokenPayload(testPayload);
        
        // 创建空的认证上下文
        AuthContext emptyContext = new AuthContext(null);
        
        // 将空的认证上下文应用到请求上下文
        emptyContext.applyToRequestContext(contextManager);
        
        // 验证请求上下文已清除
        assertFalse(contextManager.isAuthenticated());
        assertNull(contextManager.getTokenPayload());
    }
}
