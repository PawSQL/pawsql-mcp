package com.pawsql.mcp.context;

import com.pawsql.mcp.model.JwtTokenPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 请求上下文管理器测试
 */
public class RequestContextManagerTest {

    private RequestContextManager contextManager;
    private JwtTokenPayload testPayload;

    @BeforeEach
    public void setUp() {
        contextManager = new RequestContextManager();
        testPayload = new JwtTokenPayload(
                "https://test-api.pawsql.com",
                "v1",
                "testuser",
                "test-api-key-12345"
        );
    }

    @Test
    public void testSetAndGetTokenPayload() {
        // 初始状态应该是null
        assertNull(contextManager.getTokenPayload());
        assertFalse(contextManager.isAuthenticated());
        
        // 设置认证信息
        contextManager.setTokenPayload(testPayload);
        
        // 验证认证信息已设置
        assertNotNull(contextManager.getTokenPayload());
        assertTrue(contextManager.isAuthenticated());
        assertEquals(testPayload, contextManager.getTokenPayload());
    }

    @Test
    public void testGetIndividualFields() {
        // 设置认证信息
        contextManager.setTokenPayload(testPayload);
        
        // 验证各个字段
        assertEquals("https://test-api.pawsql.com", contextManager.getBaseUrl());
        assertEquals("v1", contextManager.getVersion());
        assertEquals("testuser", contextManager.getUsername());
        assertEquals("test-api-key-12345", contextManager.getApiKey());
    }

    @Test
    public void testClear() {
        // 设置认证信息
        contextManager.setTokenPayload(testPayload);
        assertTrue(contextManager.isAuthenticated());
        
        // 清除认证信息
        contextManager.clear();
        
        // 验证认证信息已清除
        assertNull(contextManager.getTokenPayload());
        assertFalse(contextManager.isAuthenticated());
        assertNull(contextManager.getBaseUrl());
        assertNull(contextManager.getVersion());
        assertNull(contextManager.getUsername());
        assertNull(contextManager.getApiKey());
    }

    @Test
    public void testGetFieldsWithNullPayload() {
        // 未设置认证信息时，各个字段应该返回null
        assertNull(contextManager.getBaseUrl());
        assertNull(contextManager.getVersion());
        assertNull(contextManager.getUsername());
        assertNull(contextManager.getApiKey());
    }
}
