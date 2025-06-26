package com.pawsql.mcp.util;

import com.pawsql.mcp.model.JwtTokenPayload;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * JWT令牌生成工具
 * 仅在开发环境中使用，用于生成测试用的JWT令牌
 * 通过设置jwt.generator.enabled=true启用
 */
@Component
@ConditionalOnProperty(name = "jwt.generator.enabled", havingValue = "true")
public class JwtTokenGenerator implements CommandLineRunner {

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Override
    public void run(String... args) {
        System.out.println("\n===== JWT令牌生成工具 =====");
        System.out.println("生成用于测试的JWT令牌...");

        // 生成测试用的JWT令牌
        generateAndPrintToken(
                "https://www.pawsql.com",
                "https://www.pawsql.com",
                "v1",
                "test@example.com",
                "test-api-key-12345"
        );

        // 生成企业版JWT令牌示例
        generateAndPrintToken(
                "https://enterprise.pawsql.com",
                "https://enterprise.pawsql.com",
                "v2",
                "enterprise@example.com",
                "enterprise-api-key-67890"
        );

        System.out.println("\n提示：将上述令牌添加到HTTP请求头中：");
        System.out.println("Authorization: Bearer <token>");
        System.out.println("===========================\n");
    }

    /**
     * 生成并打印JWT令牌
     *
     * @param baseUrl  API基础URL
     * @param version  API版本号
     * @param username 用户名
     * @param apiKey   API密钥
     */
    private void generateAndPrintToken(String baseUrl, String frontendUrl, String version, String username, String apiKey) {
        JwtTokenPayload payload = new JwtTokenPayload(baseUrl, frontendUrl, version, username, apiKey);
        String token = jwtTokenUtil.generateToken(payload);

        System.out.println("\n用户: " + username);
        System.out.println("基础URL: " + baseUrl);
        System.out.println("前端URL: " + frontendUrl);
        System.out.println("版本: " + version);
        System.out.println("API密钥: " + apiKey);
        System.out.println("令牌: " + token);
    }
}
