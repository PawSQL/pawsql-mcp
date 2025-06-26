package com.pawsql.mcp.context;

import com.pawsql.mcp.model.JwtTokenPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * MCP认证上下文管理器
 * 使用Reactor Context机制在响应式编程模型中传递认证信息
 * 适配现有的JWT认证系统，不依赖Spring Security OAuth2
 */
@Component
public class MCPAuthenticationContextManager {
    
    private static final Logger log = LoggerFactory.getLogger(MCPAuthenticationContextManager.class);
    private static final String AUTH_CONTEXT_KEY = "MCP_AUTH_CONTEXT";
    
    @Autowired
    private RequestContextManager requestContextManager;
    
    /**
     * 存储认证上下文到当前线程
     * @param tokenPayload JWT令牌负载
     * @return 包含认证上下文的Mono
     */
    public Mono<MCPAuthContext> setAuthenticationContext(JwtTokenPayload tokenPayload) {
        if (tokenPayload != null) {
            // 创建认证上下文
            Map<String, Object> claims = new HashMap<>();
            claims.put("baseUrl", tokenPayload.getBaseUrl());
            claims.put("frontendUrl", tokenPayload.getFrontendUrl());
            claims.put("edition", tokenPayload.getEdition());
            claims.put("apiKey", tokenPayload.getApiKey());
            
            MCPAuthContext context = MCPAuthContext.builder()
                .token(tokenPayload.toString()) // 使用字符串表示令牌
                .subject(tokenPayload.getUsername())
                .scopes(Collections.emptyList()) // 默认没有权限范围
                .clientId("pawsql-client") // 默认客户端ID
                .claims(claims)
                .build();
            
            log.debug("设置认证上下文: {}", context.getSubject());
            
            // 使用Reactor Context传递
            return Mono.just(context)
                .contextWrite(ctx -> ctx.put(AUTH_CONTEXT_KEY, context));
        }
        
        log.warn("无法设置认证上下文，令牌负载为空");
        return Mono.empty();
    }
    
    /**
     * 从当前线程的RequestContextManager中获取认证信息并设置到响应式上下文
     * @return 包含认证上下文的Mono
     */
    public Mono<MCPAuthContext> setAuthenticationContextFromCurrentThread() {
        JwtTokenPayload tokenPayload = requestContextManager.getTokenPayload();
        return setAuthenticationContext(tokenPayload);
    }
    
    /**
     * 从上下文获取认证信息
     * @return 包含认证上下文的Mono
     */
    public Mono<MCPAuthContext> getAuthenticationContext() {
        return Mono.deferContextual(contextView -> {
            if (contextView.hasKey(AUTH_CONTEXT_KEY)) {
                MCPAuthContext context = contextView.get(AUTH_CONTEXT_KEY);
                log.debug("从上下文获取认证信息: {}", context.getSubject());
                return Mono.just(context);
            }
            log.debug("上下文中没有认证信息");
            return Mono.empty();
        });
    }
    
    /**
     * 使用指定的认证上下文执行操作
     * @param context 认证上下文
     * @param operation 要执行的操作
     * @param <T> 操作返回类型
     * @return 操作结果
     */
    public <T> Mono<T> withAuthenticationContext(MCPAuthContext context, Mono<T> operation) {
        if (context == null) {
            log.warn("认证上下文为空，无法执行操作");
            return Mono.error(new IllegalArgumentException("认证上下文不能为空"));
        }
        
        log.debug("使用指定的认证上下文执行操作: {}", context.getSubject());
        return operation.contextWrite(ctx -> ctx.put(AUTH_CONTEXT_KEY, context));
    }
    
    /**
     * 使用JwtTokenPayload执行响应式操作
     * @param tokenPayload JWT令牌负载
     * @param operation 要执行的操作
     * @param <T> 操作返回类型
     * @return 操作结果
     */
    public <T> Mono<T> withAuthenticationContext(JwtTokenPayload tokenPayload, Mono<T> operation) {
        return setAuthenticationContext(tokenPayload)
            .flatMap(context -> withAuthenticationContext(context, operation))
            .switchIfEmpty(Mono.error(new IllegalArgumentException("无法创建认证上下文，令牌负载为空")));
    }
    
    /**
     * 清除当前上下文中的认证信息
     * @return 空Mono
     */
    public Mono<Void> clearAuthenticationContext() {
        log.debug("清除认证上下文");
        return Mono.empty().contextWrite(ctx -> ctx.delete(AUTH_CONTEXT_KEY)).then();
    }
}
