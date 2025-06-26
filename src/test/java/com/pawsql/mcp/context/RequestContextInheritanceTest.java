package com.pawsql.mcp.context;

import com.pawsql.mcp.model.JwtTokenPayload;
import com.pawsql.mcp.util.RequestContextUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 请求上下文继承测试
 * 测试InheritableThreadLocal方案在各种场景下的表现
 */
public class RequestContextInheritanceTest {

    private RequestContextManager contextManager;
    private JwtTokenPayload testPayload;
    private ThreadPoolTaskExecutor executor;

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
        
        // 创建线程池，模拟异步任务执行
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("test-async-");
        executor.initialize();
    }

    @Test
    public void testInheritanceInAsyncTasks() throws InterruptedException, ExecutionException {
        // 设置父线程的认证信息
        contextManager.setTokenPayload(testPayload);
        
        // 在子线程中验证认证信息是否被继承
        CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
            JwtTokenPayload childPayload = contextManager.getTokenPayload();
            return childPayload != null && 
                   testPayload.getUsername().equals(childPayload.getUsername()) &&
                   testPayload.getApiKey().equals(childPayload.getApiKey());
        }, executor);
        
        assertTrue(future.get(), "子线程应该继承父线程的认证信息");
    }
    
    @Test
    public void testClearInChildThreadDoesNotAffectParent() throws InterruptedException {
        // 设置父线程的认证信息
        contextManager.setTokenPayload(testPayload);
        
        // 创建闭锁，确保子线程执行完毕
        CountDownLatch latch = new CountDownLatch(1);
        
        // 在子线程中清除认证信息
        executor.execute(() -> {
            try {
                // 验证子线程继承了认证信息
                JwtTokenPayload childPayload = contextManager.getTokenPayload();
                assertNotNull(childPayload);
                assertEquals(testPayload.getUsername(), childPayload.getUsername());
                
                // 清除子线程的认证信息
                contextManager.clear();
                assertNull(contextManager.getTokenPayload());
            } finally {
                latch.countDown();
            }
        });
        
        // 等待子线程执行完毕
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        
        // 验证父线程的认证信息未受影响
        assertNotNull(contextManager.getTokenPayload());
        assertEquals(testPayload.getUsername(), contextManager.getTokenPayload().getUsername());
    }
    
    @Test
    public void testMultipleThreadsWithDifferentAuth() throws InterruptedException {
        // 创建多个不同的认证信息
        List<JwtTokenPayload> payloads = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            payloads.add(new JwtTokenPayload(
                    "https://test-api.pawsql.com",
                    "https://test-api.pawsql.com",
                    "v1",
                    "user" + i,
                    "api-key-" + i
            ));
        }
        
        // 创建闭锁，确保所有线程执行完毕
        CountDownLatch latch = new CountDownLatch(payloads.size());
        AtomicBoolean testPassed = new AtomicBoolean(true);
        
        // 在多个线程中设置不同的认证信息
        for (int i = 0; i < payloads.size(); i++) {
            final int index = i;
            executor.execute(() -> {
                try {
                    JwtTokenPayload payload = payloads.get(index);
                    contextManager.setTokenPayload(payload);
                    
                    // 验证当前线程的认证信息
                    JwtTokenPayload currentPayload = contextManager.getTokenPayload();
                    if (currentPayload == null || !payload.getUsername().equals(currentPayload.getUsername())) {
                        testPassed.set(false);
                    }
                    
                    // 模拟线程执行一段时间
                    Thread.sleep(100);
                    
                    // 再次验证认证信息未被其他线程修改
                    currentPayload = contextManager.getTokenPayload();
                    if (currentPayload == null || !payload.getUsername().equals(currentPayload.getUsername())) {
                        testPassed.set(false);
                    }
                } catch (Exception e) {
                    testPassed.set(false);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // 等待所有线程执行完毕
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(testPassed.get(), "每个线程应该维护自己的认证信息");
    }
    
    @Test
    public void testRunWithAuthSnapshot() throws InterruptedException {
        // 设置父线程的认证信息
        contextManager.setTokenPayload(testPayload);
        
        // 创建一个不同的认证信息
        JwtTokenPayload tempPayload = new JwtTokenPayload(
                "https://temp-api.pawsql.com",
                "https://temp-api.pawsql.com",
                "v2",
                "tempuser",
                "temp-api-key"
        );
        
        // 创建闭锁，确保子线程执行完毕
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> usernameInChildThread = new AtomicReference<>();
        
        // 创建认证信息快照
        JwtTokenPayload snapshot = contextManager.createAuthSnapshot();
        
        // 在子线程中使用临时认证信息
        executor.execute(() -> {
            try {
                // 使用临时认证信息执行操作
                contextManager.runWithAuth(tempPayload, () -> {
                    usernameInChildThread.set(contextManager.getUsername());
                });
                
                // 验证子线程的认证信息已恢复为快照中的认证信息
                assertEquals(testPayload.getUsername(), contextManager.getUsername());
            } finally {
                latch.countDown();
            }
        });
        
        // 等待子线程执行完毕
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        
        // 验证子线程中使用了临时认证信息
        assertEquals("tempuser", usernameInChildThread.get());
        
        // 验证父线程的认证信息未受影响
        assertEquals(testPayload.getUsername(), contextManager.getUsername());
    }
    
    @Test
    public void testRequestContextUtil() throws InterruptedException {
        // 创建闭锁，确保子线程执行完毕
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> usernameInChildThread = new AtomicReference<>();
        
        // 在子线程中使用RequestContextUtil设置认证信息
        executor.execute(() -> {
            try {
                // 使用RequestContextUtil设置认证信息并执行操作
                RequestContextUtil.executeWithAuth(testPayload, contextManager, () -> {
                    usernameInChildThread.set(contextManager.getUsername());
                });
                
                // 验证子线程的认证信息已清除
                assertNull(contextManager.getUsername());
            } finally {
                latch.countDown();
            }
        });
        
        // 等待子线程执行完毕
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        
        // 验证子线程中使用了设置的认证信息
        assertEquals(testPayload.getUsername(), usernameInChildThread.get());
    }
    
    @Test
    public void testMemoryLeakPrevention() throws InterruptedException {
        // 创建大量线程，每个线程设置不同的认证信息
        int threadCount = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.execute(() -> {
                try {
                    // 设置认证信息
                    JwtTokenPayload payload = new JwtTokenPayload(
                            "https://test-api.pawsql.com",
                            "https://test-api.pawsql.com",
                            "v1",
                            "user" + index,
                            "api-key-" + index
                    );
                    contextManager.setTokenPayload(payload);
                    
                    // 执行一些操作
                    assertNotNull(contextManager.getUsername());
                    
                    // 清除认证信息，防止内存泄漏
                    contextManager.clear();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // 等待所有线程执行完毕
        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }
}
