package com.pawsql.mcp.service;

import com.pawsql.mcp.model.ApiResult;
import com.pawsql.mcp.model.DatabaseInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class PawsqlApiService {
    private static final Logger log = LoggerFactory.getLogger(PawsqlApiService.class);
    private static final String CLOUD_API_URL = "https://www.pawsql.com";
    private static final String API_VERSION = "v1";
    private static final String API_PATH = "/api/" + API_VERSION;

    private final RestTemplate restTemplate;
    private final String apiBaseUrl;
    private String apiKey;
    private String frontendUrl;

    public PawsqlApiService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();

        String edition = getRequiredEnvVar("PAWSQL_EDITION");
        
        switch (edition.toLowerCase()) {
            case "cloud":
                this.apiBaseUrl = CLOUD_API_URL;
                String emailCloud = getRequiredEnvVar("PAWSQL_API_EMAIL");
                String passwordCloud = getRequiredEnvVar("PAWSQL_API_PASSWORD");
                initializeApiCredentials(emailCloud, passwordCloud);
                break;
            case "enterprise":
                this.apiBaseUrl = getRequiredEnvVar("PAWSQL_API_BASE_URL");
                String emailEnterprise = getRequiredEnvVar("PAWSQL_API_EMAIL");
                String passwordEnterprise = getRequiredEnvVar("PAWSQL_API_PASSWORD");
                initializeApiCredentials(emailEnterprise, passwordEnterprise);
                break;
            case "community":
                log.info("Using PawSQL Community Edition");
                this.apiBaseUrl = getRequiredEnvVar("PAWSQL_API_BASE_URL");
                initializeApiCredentials("community@pawsql.com", "community@pawsql.com");
                break;
            default:
                throw new IllegalStateException("Unsupported PawSQL edition: " + edition);
        }
    }

    private String getRequiredEnvVar(String name) {
        String value = System.getenv(name);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException(name + " environment variable is not set");
        }
        return value;
    }

    private void initializeApiCredentials(String email, String password) {
        try {
            log.info("Initializing API credentials");
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("email", email);
            requestBody.put("password", password);

            ApiResult response = executeApiCall("/getUserKey", requestBody);
            if (response != null && response.data() != null) {
                Map<String, Object> data = (Map<String, Object>) response.data();
                this.apiKey = (String) data.get("apikey");
                this.frontendUrl = (String) data.get("frontendUrl");
                log.info("API credentials initialized successfully");
            } else {
                throw new RuntimeException("Failed to get API credentials: Empty response");
            }
        } catch (Exception e) {
            log.error("Failed to get API credentials", e);
            throw new RuntimeException("Failed to get API credentials", e);
        }
    }

    private ApiResult executeApiCall(String endpoint, Map<String, ?> requestBody) {
        try {
            String url = apiBaseUrl + API_PATH + endpoint;
            ResponseEntity<ApiResult> response = restTemplate.postForEntity(url, requestBody, ApiResult.class);
            ApiResult result = response.getBody();

            if (result == null) {
                log.warn("API call returned empty response: {}", endpoint);
                return null;
            }

            log.debug("API call successful: {}, response: {}", endpoint, result);
            return result;
        } catch (Exception e) {
            log.error("API call failed: {}", endpoint, e);
            throw new RuntimeException("API call failed: " + endpoint, e);
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