package com.pawsql.mcp.service;

import com.pawsql.mcp.context.RequestContextManager;
import com.pawsql.mcp.model.ApiResult;
import com.pawsql.mcp.model.DatabaseInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.annotation.RequestScope;

import java.util.HashMap;
import java.util.Map;

@Service
@RequestScope // 确保每个请求都有独立的实例
public class PawsqlApiService {
    private static final Logger log = LoggerFactory.getLogger(PawsqlApiService.class);
    private static final String CLOUD_API_URL = "https://www.pawsql.com";
    private static final String API_PATH = "/api/";

    private final RestTemplate restTemplate;
    private String apiBaseUrl;
    private String apiKey;
    private String frontendUrl;
    private String apiVersion;
    
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
        
        this.apiBaseUrl = requestContextManager.getBaseUrl();
        this.apiVersion = requestContextManager.getVersion();
        String username = requestContextManager.getUsername();
        this.apiKey = requestContextManager.getApiKey();
        
        if (apiBaseUrl == null || apiVersion == null || username == null || apiKey == null) {
            log.error("令牌中缺少必要的认证信息");
            throw new IllegalStateException("令牌缺少必要的认证字段");
        }
        
        log.info("API服务已初始化，用户: {}", username);
        validateApiKey();
    }

    // 已移除getRequiredEnvVar方法，改为从请求上下文获取认证信息

    // 已移除initializeApiCredentials方法，改为从请求上下文获取认证信息

    private ApiResult executeApiCall(String endpoint, Map<String, ?> requestBody) {
        try {
            String url = apiBaseUrl + API_PATH + apiVersion + endpoint;
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
        return frontendUrl;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public boolean validateApiKey() {
        try {
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("userKey", apiKey);

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
            requestBody.put("userKey", apiKey);
        return requestBody;
    }
}