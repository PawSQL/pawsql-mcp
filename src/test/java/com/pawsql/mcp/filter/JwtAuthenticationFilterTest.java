package com.pawsql.mcp.filter;

import com.pawsql.mcp.context.RequestContextManager;
import com.pawsql.mcp.model.JwtTokenPayload;
import com.pawsql.mcp.util.JwtTokenUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * JWT认证过滤器测试
 */
@ExtendWith(MockitoExtension.class)
public class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenUtil jwtTokenUtil;

    @Mock
    private RequestContextManager requestContextManager;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private JwtTokenPayload testPayload;
    private String validToken;

    @BeforeEach
    public void setUp() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        testPayload = new JwtTokenPayload(
                "https://test-api.pawsql.com",
                "v1",
                "testuser",
                "test-api-key-12345"
        );
        validToken = "valid.test.token";
    }

    @Test
    public void testValidToken() throws ServletException, IOException {
        // 设置请求头
        request.addHeader("Authorization", "Bearer " + validToken);

        // Mock JWT工具类
        when(jwtTokenUtil.validateToken(validToken)).thenReturn(true);
        when(jwtTokenUtil.getTokenPayload(validToken)).thenReturn(testPayload);

        // 执行过滤器
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // 验证结果
        verify(requestContextManager).setTokenPayload(testPayload);
        verify(filterChain).doFilter(request, response);
        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
    }

    @Test
    public void testInvalidToken() throws ServletException, IOException {
        // 设置请求头
        request.addHeader("Authorization", "Bearer invalid.token");

        // Mock JWT工具类
        when(jwtTokenUtil.validateToken("invalid.token")).thenReturn(false);

        // 执行过滤器
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // 验证结果
        verify(requestContextManager, never()).setTokenPayload(any());
        verify(filterChain, never()).doFilter(request, response);
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertTrue(response.getContentAsString().contains("无效的认证令牌"));
    }

    @Test
    public void testMissingToken() throws ServletException, IOException {
        // 不设置Authorization请求头

        // 执行过滤器
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // 验证结果
        verify(requestContextManager, never()).setTokenPayload(any());
        verify(filterChain, never()).doFilter(request, response);
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertTrue(response.getContentAsString().contains("缺少认证令牌"));
    }

    @Test
    public void testTokenProcessingException() throws ServletException, IOException {
        // 设置请求头
        request.addHeader("Authorization", "Bearer " + validToken);

        // Mock JWT工具类抛出异常
        when(jwtTokenUtil.validateToken(validToken)).thenThrow(new RuntimeException("测试异常"));

        // 执行过滤器
        jwtAuthenticationFilter.doFilterInternal(request, response, filterChain);

        // 验证结果
        verify(requestContextManager, never()).setTokenPayload(any());
        verify(filterChain, never()).doFilter(request, response);
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertTrue(response.getContentAsString().contains("认证处理异常"));
    }
}
