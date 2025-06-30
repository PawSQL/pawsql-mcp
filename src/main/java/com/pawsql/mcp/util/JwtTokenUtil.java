package com.pawsql.mcp.util;

import com.pawsql.mcp.model.JwtTokenPayload;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT令牌工具类
 * 提供JWT令牌的解析、验证等功能
 */
@Component
public class JwtTokenUtil {
    private static final Logger log = LoggerFactory.getLogger(JwtTokenUtil.class);

    // 默认密钥，可通过配置文件覆盖
    @Value("${jwt.secret:gmYTZlXESyoSTicizrZoIsLi8GPRL0QHdykpDarCUtCmvbT4o7d4mLrTcV16wflprC1QfIoQF42uLFL6aVsIjQ==}")
    private String secret;

    @Value("${jwt.expiration:86400000000}")
    private long expiration;

    /**
     * 从令牌中获取数据声明
     *
     * @param token 令牌
     * @return 数据声明
     */
    private Claims getClaimsFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSignKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 从令牌中获取认证信息
     *
     * @param token 令牌
     * @return 认证信息
     */
    public JwtTokenPayload getTokenPayload(String token) {
        Claims claims = getClaimsFromToken(token);
        return new JwtTokenPayload(
                claims.get("baseUrl", String.class),
                claims.get("frontendUrl", String.class),
                claims.get("edition", String.class),
                claims.get("username", String.class),
                claims.get("apiKey", String.class)
        );
    }

    /**
     * 验证令牌是否有效
     *
     * @param token 令牌
     * @return 是否有效
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            // 验证必要字段是否存在
            if (claims.get("baseUrl") == null || 
                claims.get("edition") == null ||
                claims.get("username") == null || 
                claims.get("apiKey") == null) {
                log.warn("Token缺少必要的认证信息字段");
                return false;
            }
            
            // 验证是否过期
            return !claims.getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            log.error("Token已过期: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("不支持的Token: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.error("Token格式错误: {}", e.getMessage());
        } catch (SignatureException e) {
            log.error("Token签名验证失败: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Token验证异常: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 生成令牌（用于测试）
     *
     * @param payload 认证信息
     * @return 令牌
     */
    public String generateToken(JwtTokenPayload payload) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("baseUrl", payload.getBaseUrl());
        claims.put("frontendUrl", payload.getFrontendUrl());
        claims.put("edition", payload.getEdition());
        claims.put("username", payload.getUsername());
        claims.put("apiKey", payload.getApiKey());
        
        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignKey())
                .compact();
    }

    /**
     * 获取签名密钥
     *
     * @return 密钥
     */
    private Key getSignKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
