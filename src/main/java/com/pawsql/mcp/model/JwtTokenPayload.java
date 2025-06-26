package com.pawsql.mcp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JWT令牌载荷实体类
 * 用于存储从JWT token中解析出的认证信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JwtTokenPayload {
    /**
     * API基础URL
     */
    private String baseUrl;
    
    /**
     * 前端URL
     */
    private String frontendUrl;
    
    /**
     * 服务版本类型（cloud/enterprise/community）
     */
    private String edition;
    
    /**
     * 用户名
     */
    private String username;
    
    /**
     * API密钥
     */
    private String apiKey;
}
