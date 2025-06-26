package com.pawsql.mcp.service;

import com.pawsql.mcp.context.RequestContextManager;
import com.pawsql.mcp.model.ApiResult;
import com.pawsql.mcp.model.DatabaseInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.annotation.RequestScope;

import java.util.HashMap;
import java.util.Map;

@Service
// 移除RequestScope，改为单例模式，通过手动设置认证信息来支持多线程环境
public class PawsqlApiService {
    private static final Logger log = LoggerFactory.getLogger(PawsqlApiService.class);
    private static final String CLOUD_API_URL = "https://www.pawsql.com";
    private static final String API_PATH = "/api/";

    private final RestTemplate restTemplate;
    // 使用ThreadLocal存储每个线程的认证信息
    private final ThreadLocal<String> apiBaseUrl = new ThreadLocal<>();
    private final ThreadLocal<String> apiKey = new ThreadLocal<>();
    private final ThreadLocal<String> frontendUrl = new ThreadLocal<>();
    private final ThreadLocal<String> apiVersion = new ThreadLocal<>();
    
    @Autowired
    private RequestContextManager requestContextManager;

    public PawsqlApiService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
    }
    
    /**
     * 初始化API服务
     * 从请求上下文中获取认证信息
     * 在每次请求时调用
     */
    public void initialize() {
        if (requestContextManager.getTokenPayload() == null) {
            log.error("请求上下文中未找到认证信息");
            throw new IllegalStateException("需要认证令牌");
        }
        
        this.apiBaseUrl.set(requestContextManager.getBaseUrl());
        this.apiVersion.set(requestContextManager.getVersion());
        String username = requestContextManager.getUsername();
        this.apiKey.set(requestContextManager.getApiKey());
        
        if (apiBaseUrl.get() == null || apiVersion.get() == null || username == null || apiKey.get() == null) {
            log.error("令牌中缺少必要的认证信息");
            throw new IllegalStateException("令牌缺少必要的认证字段");
        }
        
        log.info("API服务已初始化，用户: {}", username);
        validateApiKey();
    }
    
    /**
     * 手动设置认证信息
     * 用于在非HTTP请求线程中使用API服务
     *
     * @param baseUrl API基础URL
     * @param version API版本
     * @param apiKey API密钥
     */
    public void setAuthInfo(String baseUrl, String version, String apiKey) {
        this.apiBaseUrl.set(baseUrl);
        this.apiVersion.set(version);
        this.apiKey.set(apiKey);
        
        if (baseUrl == null || version == null || apiKey == null) {
            throw new IllegalArgumentException("认证信息不能为空");
        }
        
        log.info("API服务已手动初始化");
    }

    // 已移除getRequiredEnvVar方法，改为从请求上下文获取认证信息

    // 已移除initializeApiCredentials方法，改为从请求上下文获取认证信息

    private ApiResult executeApiCall(String endpoint, Map<String, ?> requestBody) {
        try {
            // 检查是否已初始化
            if (apiBaseUrl.get() == null || apiVersion.get() == null) {
                log.error("API服务未初始化，请先调用initialize()或setAuthInfo()");
                throw new IllegalStateException("API服务未初始化");
            }
            
            String url = apiBaseUrl.get() + API_PATH + apiVersion.get() + endpoint;
            ResponseEntity<ApiResult> response = restTemplate.postForEntity(url, requestBody, ApiResult.class);
            ApiResult result = response.getBody();

            if (result == null) {
                log.warn("API调用返回空响应: {}", endpoint);
                return null;
            }

            log.debug("API调用成功: {}, 响应: {}", endpoint, result);
            return result;
        } catch (Exception e) {
            log.error("API调用失败: {}", endpoint, e);
            throw new RuntimeException("API调用失败: " + endpoint, e);
        }
    }

    public String getFrontendUrl() {
        return frontendUrl.get();
    }

    public String getApiBaseUrl() {
        return apiBaseUrl.get();
    }

    public boolean validateApiKey() {
        try {
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("userKey", apiKey.get());

            ApiResult response = executeApiCall("/validateUserKey", requestBody);
            if (response != null && response.data() != null) {
                boolean isValid = (boolean) response.data();
                log.info("API key validation result: {}", isValid);
                return isValid;
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to validate API key", e);
            return false;
        }
    }

    public ApiResult getAnalysisSummary(String analysisId) {
        Map<String, String> requestBody = createAuthenticatedRequest();
        requestBody.put("analysisId", analysisId);
        log.info("Getting SQL analysis results: {}", analysisId);
        return executeApiCall("/getAnalysisSummary", requestBody);
    }

    public ApiResult getStatementDetails(String analysisStmtId) {
        Map<String, String> requestBody = createAuthenticatedRequest();
        requestBody.put("analysisStmtId", analysisStmtId);
        log.info("Getting SQL statement optimization details: {}", analysisStmtId);
        return executeApiCall("/getStatementDetails", requestBody);
    }

    public String createWorkspace(DatabaseInfo dbInfo) {
        try {
            log.info("Creating workspace: {}", dbInfo);
            Map<String, String> requestBody = createAuthenticatedRequest();
            requestBody.put("mode", "offline");
            requestBody.put("dbType", dbInfo.getDbType());
            requestBody.put("ddlText", dbInfo.getDdlText());

            ApiResult response = executeApiCall("/createWorkspace", requestBody);
            if (response != null && response.data() != null) {
                Map<String, Object> data = (Map<String, Object>) response.data();
                String workspaceId = (String) data.get("workspaceId");
                log.info("Workspace created successfully: {}", workspaceId);
                return workspaceId;
            }
            throw new RuntimeException("Failed to create workspace: Empty response");
        } catch (Exception e) {
            log.error("Failed to create workspace", e);
            throw new RuntimeException("Failed to create workspace: " + e.getMessage(), e);
        }
    }

    public ApiResult createAnalysis(String sql, String workspaceId, String dbType, boolean validateFlag) {
        try {
            log.info("Sending SQL optimization request: {}, workspaceId: {}", sql, workspaceId);
            Map<String, String> requestBody = createAuthenticatedRequest();
            requestBody.put("workload", sql);
            requestBody.put("queryMode", "plain_sql");
            requestBody.put("dbType", dbType);

            if (workspaceId != null) {
                requestBody.put("workspace", workspaceId);
                requestBody.put("validateFlag", String.valueOf(validateFlag));
            }

            return executeApiCall("/createAnalysis", requestBody);
        } catch (Exception e) {
            log.error("SQL optimization request failed", e);
            throw new RuntimeException("SQL optimization service call failed", e);
        }
    }

    public ApiResult listWorkspaces(int pageNumber, int pageSize) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("userKey", apiKey);
        requestBody.put("pageNumber", pageNumber);
        requestBody.put("pageSize", pageSize);
        log.info("Querying workspace list: pageNumber={}, pageSize={}", pageNumber, pageSize);
        return executeApiCall("/listWorkspaces", requestBody);
    }

    private Map<String, String> createAuthenticatedRequest() {
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("userKey", apiKey.get());
        return requestBody;
    }
}