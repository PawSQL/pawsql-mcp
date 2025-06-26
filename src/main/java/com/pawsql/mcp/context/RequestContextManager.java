package com.pawsql.mcp.context;

import com.pawsql.mcp.model.JwtTokenPayload;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * 请求上下文管理器
 * 使用@RequestScope确保每个HTTP请求都有独立的实例
 * 用于存储当前请求的认证信息
 */
@Component
@RequestScope(proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RequestContextManager {
    
    /**
     * 当前请求的认证信息
     */
    private JwtTokenPayload tokenPayload;
    
    /**
     * 设置认证信息
     *
     * @param tokenPayload 认证信息
     */
    public void setTokenPayload(JwtTokenPayload tokenPayload) {
        this.tokenPayload = tokenPayload;
    }
    
    /**
     * 获取认证信息
     *
     * @return 认证信息
     */
    public JwtTokenPayload getTokenPayload() {
        return tokenPayload;
    }
    
    /**
     * 获取API基础URL
     *
     * @return API基础URL
     */
    public String getBaseUrl() {
        return tokenPayload != null ? tokenPayload.getBaseurl() : null;
    }
    
    /**
     * 获取API版本号
     *
     * @return API版本号
     */
    public String getVersion() {
        return tokenPayload != null ? tokenPayload.getVersion() : null;
    }
    
    /**
     * 获取用户名
     *
     * @return 用户名
     */
    public String getUsername() {
        return tokenPayload != null ? tokenPayload.getUsername() : null;
    }
    
    /**
     * 获取API密钥
     *
     * @return API密钥
     */
    public String getApiKey() {
        return tokenPayload != null ? tokenPayload.getApikey() : null;
    }
    
    /**
     * 检查是否已设置认证信息
     *
     * @return 是否已设置认证信息
     */
    public boolean isAuthenticated() {
        return tokenPayload != null;
    }
    
    /**
     * 清除认证信息
     */
    public void clear() {
        this.tokenPayload = null;
    }
}
