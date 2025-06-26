package com.pawsql.mcp.filter;

import com.pawsql.mcp.context.RequestContextManager;
import com.pawsql.mcp.model.JwtTokenPayload;
import com.pawsql.mcp.util.JwtTokenUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT认证过滤器
 * 拦截请求，从请求头中提取JWT token，解析并验证token，然后将认证信息存储到请求上下文中
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    
    @Autowired
    private JwtTokenUtil jwtTokenUtil;
    
    @Autowired
    private RequestContextManager requestContextManager;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) 
            throws ServletException, IOException {
        try {
            String authHeader = request.getHeader(AUTHORIZATION_HEADER);
            if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
                String token = authHeader.substring(BEARER_PREFIX.length());
                
                // 验证token
                if (jwtTokenUtil.validateToken(token)) {
                    // 解析token
                    JwtTokenPayload tokenPayload = jwtTokenUtil.getTokenPayload(token);
                    
                    // 存储到请求上下文
                    requestContextManager.setTokenPayload(tokenPayload);
                    
                    log.info("JWT token处理成功，用户: {}", tokenPayload.getUsername());
                } else {
                    log.warn("无效的JWT token");
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write("无效的认证令牌");
                    return;
                }
            } else {
                log.warn("请求头中未找到JWT token");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("缺少认证令牌");
                return;
            }
        } catch (Exception e) {
            log.error("JWT token处理异常", e);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("认证处理异常: " + e.getMessage());
            return;
        }
        
        filterChain.doFilter(request, response);
    }
}
