package com.pawsql.mcp.util;

import com.pawsql.mcp.model.JwtTokenPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JWT令牌工具类测试
 */
public class JwtTokenUtilTest {

    private JwtTokenUtil jwtTokenUtil;
    private final String testSecret = "e07437cb-09be-47d0-9336-150e21ebbecd";
    private final long testExpiration = 36000000000l; // 1小时

    @BeforeEach
    public void setUp() {
        jwtTokenUtil = new JwtTokenUtil();
        ReflectionTestUtils.setField(jwtTokenUtil, "secret", testSecret);
        ReflectionTestUtils.setField(jwtTokenUtil, "expiration", testExpiration);
    }

    @Test
    public void testGenerateAndValidateToken() {
        // 准备测试数据
        JwtTokenPayload payload = new JwtTokenPayload(
                "https://pawsql.com",
                "v1",
                "Test@pawsql.com",
                "2575BC23-BFFEAEA3-8D1E5B1A-ACF2FAE5"
        );

        // 生成token
        String token = jwtTokenUtil.generateToken(payload);
        System.out.println(token);
        // 验证token不为空
        assertNotNull(token);
        
        // 验证token有效
        boolean isValid = jwtTokenUtil.validateToken(token);
        assertTrue(isValid, "生成的token应该是有效的");
    }

    @Test
    public void testGetTokenPayload() {
        // 准备测试数据
        JwtTokenPayload originalPayload = new JwtTokenPayload(
                "https://test-api.pawsql.com",
                "v1",
                "testuser",
                "test-api-key-12345"
        );

        // 生成token
        String token = jwtTokenUtil.generateToken(originalPayload);
        
        // 从token中获取payload
        JwtTokenPayload extractedPayload = jwtTokenUtil.getTokenPayload(token);
        
        // 验证提取的payload与原始payload一致
        assertNotNull(extractedPayload);
        assertEquals(originalPayload.getBaseurl(), extractedPayload.getBaseurl());
        assertEquals(originalPayload.getVersion(), extractedPayload.getVersion());
        assertEquals(originalPayload.getUsername(), extractedPayload.getUsername());
        assertEquals(originalPayload.getApikey(), extractedPayload.getApikey());
    }

    @Test
    public void testInvalidToken() {
        // 准备一个无效的token
        String invalidToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
        
        // 验证token无效
        boolean isValid = jwtTokenUtil.validateToken(invalidToken);
        assertFalse(isValid, "无效的token应该验证失败");
    }

    @Test
    public void testMissingRequiredFields() {
        // 准备缺少必要字段的payload
        JwtTokenPayload incompletePayload = new JwtTokenPayload(
                "https://test-api.pawsql.com",
                null, // 缺少version
                "testuser",
                "test-api-key-12345"
        );

        // 生成token
        String token = jwtTokenUtil.generateToken(incompletePayload);
        
        // 验证token无效（缺少必要字段）
        boolean isValid = jwtTokenUtil.validateToken(token);
        assertFalse(isValid, "缺少必要字段的token应该验证失败");
    }
}
