package com.pawsql.mcp.context;

import com.pawsql.mcp.model.JwtTokenPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 请求上下文管理器测试
 */
public class RequestContextManagerTest {

    private RequestContextManager contextManager;
    private JwtTokenPayload testPayload;
    private JwtTokenPayload anotherPayload;

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
        
        anotherPayload = new JwtTokenPayload(
                "https://another-api.pawsql.com",
                "https://another-api.pawsql.com",
                "v2",
                "anotheruser",
                "another-api-key-67890"
        );
        
        // 确保每个测试开始时上下文是干净的
        contextManager.clear();
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
    
    @Test
    public void testInheritableThreadLocal() throws InterruptedException {
        // 设置父线程的认证信息
        contextManager.setTokenPayload(testPayload);
        
        // 用于等待子线程执行完成
        CountDownLatch latch = new CountDownLatch(1);
        
        // 用于从子线程中获取结果
        AtomicReference<JwtTokenPayload> childThreadPayload = new AtomicReference<>();
        
        // 创建并启动子线程
        Thread childThread = new Thread(() -> {
            try {
                // 子线程应该自动继承父线程的认证信息
                childThreadPayload.set(contextManager.getTokenPayload());
            } finally {
                latch.countDown();
            }
        });
        
        childThread.start();
        latch.await(); // 等待子线程执行完成
        
        // 验证子线程继承了父线程的认证信息
        assertNotNull(childThreadPayload.get());
        assertEquals(testPayload.getBaseUrl(), childThreadPayload.get().getBaseUrl());
        assertEquals(testPayload.getEdition(), childThreadPayload.get().getEdition());
        assertEquals(testPayload.getUsername(), childThreadPayload.get().getUsername());
        assertEquals(testPayload.getApiKey(), childThreadPayload.get().getApiKey());
    }
    
    @Test
    public void testCreateAuthSnapshot() {
        // 设置认证信息
        contextManager.setTokenPayload(testPayload);
        
        // 创建快照
        JwtTokenPayload snapshot = contextManager.createAuthSnapshot();
        
        // 验证快照内容
        assertNotNull(snapshot);
        assertEquals(testPayload.getBaseUrl(), snapshot.getBaseUrl());
        assertEquals(testPayload.getEdition(), snapshot.getEdition());
        assertEquals(testPayload.getUsername(), snapshot.getUsername());
        assertEquals(testPayload.getApiKey(), snapshot.getApiKey());
        
        // 验证快照是一个新对象，而不是原对象的引用
        assertNotSame(testPayload, snapshot);
    }
    
    @Test
    public void testRunWithAuth() {
        // 初始设置认证信息
        contextManager.setTokenPayload(testPayload);
        
        // 使用临时认证信息执行操作
        contextManager.runWithAuth(anotherPayload, () -> {
            // 验证临时认证信息已设置
            assertEquals(anotherPayload.getUsername(), contextManager.getUsername());
            assertEquals(anotherPayload.getApiKey(), contextManager.getApiKey());
        });
        
        // 验证操作完成后恢复了原有认证信息
        assertEquals(testPayload.getUsername(), contextManager.getUsername());
        assertEquals(testPayload.getApiKey(), contextManager.getApiKey());
    }
    
    @Test
    public void testSupplyWithAuth() {
        // 初始设置认证信息
        contextManager.setTokenPayload(testPayload);
        
        // 使用临时认证信息执行操作并获取结果
        String result = contextManager.supplyWithAuth(anotherPayload, () -> {
            // 验证临时认证信息已设置
            assertEquals(anotherPayload.getUsername(), contextManager.getUsername());
            return "操作成功";
        });
        
        // 验证操作完成后恢复了原有认证信息
        assertEquals(testPayload.getUsername(), contextManager.getUsername());
        assertEquals("操作成功", result);
    }
    
    @Test
    public void testClearInChildThread() throws InterruptedException {
        // 设置父线程的认证信息
        contextManager.setTokenPayload(testPayload);
        
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Boolean> childThreadClearResult = new AtomicReference<>();
        
        Thread childThread = new Thread(() -> {
            try {
                // 验证子线程继承了认证信息
                assertNotNull(contextManager.getTokenPayload());
                
                // 在子线程中清除认证信息
                contextManager.clear();
                
                // 验证认证信息已清除
                childThreadClearResult.set(contextManager.getTokenPayload() == null);
            } finally {
                latch.countDown();
            }
        });
        
        childThread.start();
        latch.await();
        
        // 验证子线程成功清除了认证信息
        assertTrue(childThreadClearResult.get());
        
        // 验证父线程的认证信息不受影响
        assertNotNull(contextManager.getTokenPayload());
    }
}
