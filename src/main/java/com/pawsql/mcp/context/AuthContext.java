package com.pawsql.mcp.context;

import com.pawsql.mcp.model.JwtTokenPayload;

/**
 * 认证上下文
 * 用于在方法调用之间显式传递认证信息
 */
public class AuthContext {
    
    private final JwtTokenPayload tokenPayload;
    
    /**
     * 创建认证上下文
     *
     * @param tokenPayload 认证信息
     */
    public AuthContext(JwtTokenPayload tokenPayload) {
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
        return tokenPayload != null ? tokenPayload.getBaseUrl() : null;
    }
    
    /**
     * 获取前端URL
     *
     * @return 前端URL
     */
    public String getFrontendUrl() {
        return tokenPayload != null ? tokenPayload.getFrontendUrl() : null;
    }
    
    /**
     * 获取服务版本类型
     *
     * @return 服务版本类型
     */
    public String getEdition() {
        return tokenPayload != null ? tokenPayload.getEdition() : null;
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
        return tokenPayload != null ? tokenPayload.getApiKey() : null;
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
     * 从RequestContextManager创建认证上下文
     *
     * @param contextManager 请求上下文管理器
     * @return 认证上下文，如果没有认证信息则返回null
     */
    public static AuthContext fromRequestContext(RequestContextManager contextManager) {
        JwtTokenPayload payload = contextManager.getTokenPayload();
        return payload != null ? new AuthContext(payload) : null;
    }
    
    /**
     * 将认证上下文应用到RequestContextManager
     *
     * @param contextManager 请求上下文管理器
     */
    public void applyToRequestContext(RequestContextManager contextManager) {
        if (tokenPayload != null) {
            contextManager.setTokenPayload(tokenPayload);
        } else {
            contextManager.clear();
        }
    }
}
