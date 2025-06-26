package com.pawsql.mcp.context;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * MCP认证上下文
 * 用于在响应式编程模型中传递认证信息
 */
@Data
@Builder
public class MCPAuthContext {
    private String token;
    private String subject;
    private List<String> scopes;
    private String clientId;
    private Map<String, Object> claims;
    
    /**
     * 检查认证上下文是否有效
     * @return 如果认证上下文有效则返回true，否则返回false
     */
    public boolean isValid() {
        return token != null && !token.isEmpty() && 
               subject != null && !subject.isEmpty();
    }
    
    /**
     * 获取指定声明
     * @param name 声明名称
     * @return 声明值
     */
    public Object getClaim(String name) {
        return claims != null ? claims.get(name) : null;
    }
    
    /**
     * 获取指定声明为字符串
     * @param name 声明名称
     * @return 声明值字符串
     */
    public String getClaimAsString(String name) {
        Object value = getClaim(name);
        return value != null ? value.toString() : null;
    }
    
    /**
     * 获取API密钥
     * @return API密钥
     */
    public String getApiKey() {
        return getClaimAsString("apiKey");
    }
    
    /**
     * 获取基础URL
     * @return 基础URL
     */
    public String getBaseUrl() {
        return getClaimAsString("baseUrl");
    }
    
    /**
     * 获取前端URL
     * @return 前端URL
     */
    public String getFrontendUrl() {
        return getClaimAsString("frontendUrl");
    }
    
    /**
     * 获取版本
     * @return 版本
     */
    public String getEdition() {
        return getClaimAsString("edition");
    }
}
