package com.pawsql.mcp.context;

import com.pawsql.mcp.model.JwtTokenPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 请求上下文管理器
 * 使用InheritableThreadLocal存储认证信息，确保线程安全
 * 支持在Web请求和非Web线程中使用，子线程自动继承父线程的认证信息
 */
@Component
public class RequestContextManager {
    
    private static final Logger log = LoggerFactory.getLogger(RequestContextManager.class);
    
    /**
     * 使用InheritableThreadLocal存储认证信息，确保线程安全
     * InheritableThreadLocal允许子线程自动继承父线程的值
     */
    private static final InheritableThreadLocal<JwtTokenPayload> TOKEN_PAYLOAD_HOLDER = new InheritableThreadLocal<>();
    
    /**
     * 设置认证信息
     *
     * @param tokenPayload 认证信息
     */
    public void setTokenPayload(JwtTokenPayload tokenPayload) {
        log.debug("设置线程[{}]的认证信息", Thread.currentThread().getName());
        TOKEN_PAYLOAD_HOLDER.set(tokenPayload);
    }
    
    /**
     * 获取认证信息
     *
     * @return 认证信息
     */
    public JwtTokenPayload getTokenPayload() {
        JwtTokenPayload payload = TOKEN_PAYLOAD_HOLDER.get();
        if (payload == null) {
            log.debug("线程[{}]未设置认证信息", Thread.currentThread().getName());
        }
        return payload;
    }
    
    /**
     * 获取API基础URL
     *
     * @return API基础URL
     */
    public String getBaseUrl() {
        JwtTokenPayload payload = TOKEN_PAYLOAD_HOLDER.get();
        return payload != null ? payload.getBaseUrl() : null;
    }
    
    /**
     * 获取前端URL
     *
     * @return 前端URL
     */
    public String getFrontendUrl() {
        JwtTokenPayload payload = TOKEN_PAYLOAD_HOLDER.get();
        return payload != null ? payload.getFrontendUrl() : null;
    }
    
    /**
     * 获取服务版本类型（cloud/enterprise/community）
     *
     * @return 服务版本类型
     */
    public String getVersion() {
        JwtTokenPayload payload = TOKEN_PAYLOAD_HOLDER.get();
        return payload != null ? payload.getEdition() : null;
    }
    
    /**
     * 获取用户名
     *
     * @return 用户名
     */
    public String getUsername() {
        JwtTokenPayload payload = TOKEN_PAYLOAD_HOLDER.get();
        return payload != null ? payload.getUsername() : null;
    }
    
    /**
     * 获取API密钥
     *
     * @return API密钥
     */
    public String getApiKey() {
        JwtTokenPayload payload = TOKEN_PAYLOAD_HOLDER.get();
        return payload != null ? payload.getApiKey() : null;
    }
    
    /**
     * 检查是否已设置认证信息
     *
     * @return 是否已设置认证信息
     */
    public boolean isAuthenticated() {
        return TOKEN_PAYLOAD_HOLDER.get() != null;
    }
    
    /**
     * 清除认证信息
     */
    public void clear() {
        log.debug("清除线程[{}]的认证信息", Thread.currentThread().getName());
        TOKEN_PAYLOAD_HOLDER.remove();
    }
    
    /**
     * 复制认证信息到当前线程
     * 用于在创建新线程时显式传递认证信息
     * 注意：使用InheritableThreadLocal后，子线程会自动继承父线程的值，
     * 此方法主要用于非父子线程关系的情况
     * 
     * @param sourcePayload 源认证信息
     */
    public void copyAuthInfo(JwtTokenPayload sourcePayload) {
        if (sourcePayload != null) {
            log.debug("复制认证信息到线程[{}]", Thread.currentThread().getName());
            TOKEN_PAYLOAD_HOLDER.set(sourcePayload);
        }
    }
    
    /**
     * 创建认证信息的快照
     * 用于保存当前线程的认证信息，以便后续使用
     * 
     * @return 当前线程的认证信息快照
     */
    public JwtTokenPayload createAuthSnapshot() {
        JwtTokenPayload payload = TOKEN_PAYLOAD_HOLDER.get();
        if (payload != null) {
            log.debug("创建线程[{}]的认证信息快照", Thread.currentThread().getName());
            return new JwtTokenPayload(
                payload.getBaseUrl(),
                payload.getFrontendUrl(),
                payload.getEdition(),
                payload.getUsername(),
                payload.getApiKey()
            );
        }
        return null;
    }
    
    /**
     * 在指定的操作执行期间使用提供的认证信息
     * 执行完成后恢复原有认证信息
     * 
     * @param tempPayload 临时认证信息
     * @param runnable 要执行的操作
     */
    public void runWithAuth(JwtTokenPayload tempPayload, Runnable runnable) {
        // 保存当前认证信息
        JwtTokenPayload originalPayload = TOKEN_PAYLOAD_HOLDER.get();
        try {
            // 设置临时认证信息
            if (tempPayload != null) {
                setTokenPayload(tempPayload);
            } else {
                clear();
            }
            
            // 执行操作
            runnable.run();
        } finally {
            // 恢复原有认证信息
            if (originalPayload != null) {
                setTokenPayload(originalPayload);
            } else {
                clear();
            }
        }
    }
    
    /**
     * 在指定的操作执行期间使用提供的认证信息
     * 执行完成后恢复原有认证信息
     * 
     * @param tempPayload 临时认证信息
     * @param supplier 要执行的操作
     * @param <T> 返回值类型
     * @return 操作的返回值
     */
    public <T> T supplyWithAuth(JwtTokenPayload tempPayload, java.util.function.Supplier<T> supplier) {
        // 保存当前认证信息
        JwtTokenPayload originalPayload = TOKEN_PAYLOAD_HOLDER.get();
        try {
            // 设置临时认证信息
            if (tempPayload != null) {
                setTokenPayload(tempPayload);
            } else {
                clear();
            }
            
            // 执行操作并返回结果
            return supplier.get();
        } finally {
            // 恢复原有认证信息
            if (originalPayload != null) {
                setTokenPayload(originalPayload);
            } else {
                clear();
            }
        }
    }
}
