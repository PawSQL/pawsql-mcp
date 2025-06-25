package com.pawsql.mcp.service;

import com.pawsql.mcp.model.ApiResult;
import com.pawsql.mcp.model.DatabaseInfo;
import com.pawsql.mcp.model.UserSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.annotation.RequestScope;

import java.util.HashMap;
import java.util.Map;

@Service
@RequestScope
public class PawsqlApiService {
    private static final Logger log = LoggerFactory.getLogger(PawsqlApiService.class);
    private static final String CLOUD_API_URL = "https://www.pawsql.com";
    private static final String API_VERSION = "v1";
    private static final String API_PATH = "/api/" + API_VERSION;

    private final RestTemplate restTemplate;
    private String apiBaseUrl;
    private String apiKey;
    private String frontendUrl;
    private UserSession currentUserSession;

    public PawsqlApiService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
        
        // In the multi-user version, we don't initialize credentials here
        // Instead, we set them when setCurrentUserSession is called
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

    /**
     * Create an authenticated request body with the current user's API key.
     * 
     * @param username the username of the current user
     * @return Map containing the userKey parameter
     */
    private Map<String, String> createAuthenticatedRequest() {
        Map<String, String> requestBody = new HashMap<>();
        
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("API key is not set, authentication will likely fail");
        } else {
            requestBody.put("userKey", apiKey);
        }
        
        return requestBody;
    }
    
    /**
     * Set the current user session for this service instance.
     * This method should be called before any API calls are made.
     * 
     * @param userSession The user session to use for API calls
     */
    public void setCurrentUserSession(UserSession userSession) {
        if (userSession == null) {
            throw new IllegalArgumentException("User session cannot be null");
        }
        
        this.currentUserSession = userSession;
        this.apiKey = userSession.getApiKey();
        this.apiBaseUrl = userSession.getApiBaseUrl();
        
        log.info("Set current user session: {}", userSession.getEmail());
    }
    
    /**
     * Get API key for a user by authenticating with email and password.
     * 
     * @param email User's email
     * @param password User's password
     * @param edition PawSQL edition
     * @param apiBaseUrl API base URL
     * @return API key if authentication is successful, null otherwise
     */
    public String getApiKey(String email, String password, String edition, String apiBaseUrl) {
        try {
            log.info("Getting API key for user: {}, edition: {}", email, edition);
            
            // Set temporary API base URL for this call
            String originalApiBaseUrl = this.apiBaseUrl;
            this.apiBaseUrl = apiBaseUrl;
            
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("email", email);
            requestBody.put("password", password);

            ApiResult response = executeApiCall("/getUserKey", requestBody);
            
            // Restore original API base URL
            this.apiBaseUrl = originalApiBaseUrl;
            
            if (response != null && response.data() != null) {
                Map<String, Object> data = (Map<String, Object>) response.data();
                String apiKey = (String) data.get("apikey");
                this.frontendUrl = (String) data.get("frontendUrl");
                log.info("API key obtained successfully for user: {}", email);
                return apiKey;
            } else {
                log.warn("Failed to get API key: Empty response");
                return null;
            }
        } catch (Exception e) {
            log.error("Failed to get API key", e);
            return null;
        }
    }
}