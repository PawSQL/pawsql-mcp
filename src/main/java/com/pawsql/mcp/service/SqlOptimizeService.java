package com.pawsql.mcp.service;

import ch.qos.logback.core.util.StringUtil;
import com.pawsql.mcp.enums.DefinitionEnum;
import com.pawsql.mcp.model.ApiResult;
import com.pawsql.mcp.model.DatabaseInfo;
import com.pawsql.mcp.model.UserSession;
import com.pawsql.mcp.service.AuthenticationService;
import com.pawsql.mcp.service.PawsqlApiService;
import io.micrometer.common.util.StringUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.pawsql.mcp.util.ReactorContextPropagator;

@Service
public class SqlOptimizeService {
    
    // Static session store for background thread access
    // This is a fallback mechanism when thread-local context is not available
    private static final ConcurrentHashMap<String, UserSession> SESSION_STORE = new ConcurrentHashMap<>();
    private static final Logger log = LoggerFactory.getLogger(SqlOptimizeService.class);
    private final PawsqlApiService apiService;
    private final UserContextManager userContextManager;
    private final ThreadLocalUserContext threadLocalUserContext;
    private final AuthenticationService authenticationService;
    private final ReactorContextPropagator reactorContextPropagator;
    
    // Static session store to ensure sessions are available across all threads
    // This is a fallback mechanism when thread-local and reactive contexts fail
    private static final Map<String, UserSession> sessionStore = new ConcurrentHashMap<>();

    public SqlOptimizeService(PawsqlApiService apiService, UserContextManager userContextManager, 
                          ThreadLocalUserContext threadLocalUserContext, AuthenticationService authenticationService,
                          ReactorContextPropagator reactorContextPropagator) {
        this.apiService = apiService;
        this.userContextManager = userContextManager;
        this.threadLocalUserContext = threadLocalUserContext;
        this.authenticationService = authenticationService;
        this.reactorContextPropagator = reactorContextPropagator;
    }
    
    /**
     * Sets the user session for tool execution.
     * This is called by the SseController when a user connects to the SSE stream.
     * 
     * @param session The user session
     */
    public void setUserSessionForTools(UserSession session) {
        if (session == null) {
            log.warn("Cannot set null user session for tools");
            return;
        }
        
        // Set the user session in thread-local context
        threadLocalUserContext.setUserSession(session);
        
        // Also set it for the API service
        apiService.setCurrentUserSession(session);
        
        // Store in the static session store for background thread access
        // This is a fallback mechanism when thread-local context is not available
        String sessionId = session.getSessionId();
        if (sessionId != null && !sessionId.isEmpty()) {
            SESSION_STORE.put(sessionId, session);
            log.debug("Stored user session in static store with ID {}: {}", sessionId, session.getEmail());
        } else {
            log.warn("Cannot store user session in static store: session ID is null or empty");
        }
        
        // Create a reactor context with the user session if available
        if (reactorContextPropagator != null) {
            reactorContextPropagator.createContext(session, sessionId);
            log.debug("Created reactor context for user session: {} in thread: {}", 
                    session.getEmail(), Thread.currentThread().getName());
        }
        
        log.info("User session set for tools: {} (session ID: {})", session.getEmail(), session.getSessionId());
    }

    @Tool(
            name = "list_workspaces",
            description = "List current workspaces and return their basic information in a markdown table."
    )
    public ApiResult listWorkspaces() {
        try {
            // Ensure user context is available
            ensureUserContext();
            
            String workspaceListMarkdown = generateWorkspaceListMarkdown();
            return workspaceListMarkdown != null ?
                    new ApiResult(200, "Successfully retrieved workspace list", workspaceListMarkdown) :
                    new ApiResult(200, "No workspaces available", null);
        } catch (Exception e) {
            log.error("Failed to retrieve workspace list", e);
            return new ApiResult(500, "Failed to retrieve workspace list: " + e.getMessage(), null);
        }
    }

    @Tool(
            name = "get_workspace_info",
            description = "Get workspace information by name or ID, including ID, database type, etc. Returns a notification if the workspace is not found."
    )
    public ApiResult getWorkspaceInfo(
            @Schema(description = "Workspace name", required = false)
            @ToolParam(required = false)
            String workspaceName,
            @Schema(description = "Workspace ID", required = false)
            @ToolParam(required = false)
            String workspaceId
    ) {
        // Ensure user context is available
        ensureUserContext();
        try {
            ApiResult workspaces = apiService.listWorkspaces(1, 100);
            if (workspaces == null || workspaces.data() == null) {
                return new ApiResult(404, "No workspaces found", null);
            }

            Map<String, Object> workspaceData = (Map<String, Object>) workspaces.data();
            List<Map<String, Object>> records = (List<Map<String, Object>>) workspaceData.get("records");
            if (records == null || records.isEmpty()) {
                return new ApiResult(404, "No workspaces found", null);
            }

            if (StringUtils.isBlank(workspaceName) && StringUtils.isBlank(workspaceId)) {
                return new ApiResult(400, "Workspace name and ID cannot both be empty", null);
            }

            for (Map<String, Object> workspace : records) {
                String name = (String) workspace.get("workspaceName");
                String id = String.valueOf(workspace.get("workspaceId"));
                if ((workspaceName != null && workspaceName.equals(name)) ||
                        (workspaceId != null && workspaceId.equals(id))) {
                    Map<String, Object> workspaceInfo = new LinkedHashMap<>();
                    workspaceInfo.put("workspaceId", workspace.get("workspaceId"));
                    workspaceInfo.put("workspaceName", workspace.get("workspaceName"));
                    workspaceInfo.put("dbType", workspace.get("dbType"));
                    workspaceInfo.put("canValidate", workspace.get("dbHost") != null);
                    workspaceInfo.put("status", workspace.get("status"));
                    return new ApiResult(200, "Workspace found successfully", workspaceInfo);
                }
            }

            String errorMsg = workspaceName != null ?
                    "Workspace with name '" + workspaceName + "' not found" :
                    "Workspace with ID '" + workspaceId + "' not found";
            return new ApiResult(404, errorMsg, null);
        } catch (Exception e) {
            log.error("Failed to find workspace information", e);
            return new ApiResult(500, "Failed to find workspace information: " + e.getMessage(), null);
        }
    }

    private String generateWorkspaceListMarkdown() {
        try {
            ApiResult workspaces = apiService.listWorkspaces(1, 10);
            if (workspaces == null || workspaces.data() == null) return null;

            Map<String, Object> workspaceData = (Map<String, Object>) workspaces.data();
            List<Map<String, Object>> records = (List<Map<String, Object>>) workspaceData.get("records");
            if (records == null || records.isEmpty()) return null;

            return buildWorkspaceMarkdownTable(records);
        } catch (Exception e) {
            log.error("Failed to generate workspace list markdown", e);
            return null;
        }
    }

    private String buildWorkspaceMarkdownTable(List<Map<String, Object>> records) {
        StringBuilder markdownBuilder = new StringBuilder()
                .append("\n## Workspace List\n")
                .append("| Workspace Name | Workspace ID | Database Type | Can Validate Optimization | Status |\n")
                .append("|---------------|--------------|--------------|------------------------|--------|\n");

        for (Map<String, Object> workspace : records) {
            String tempDbType = workspace.get("dbType") != null ? workspace.get("dbType").toString() : "-";
            boolean canValidate = workspace.get("dbHost") != null;
            String validationStatus = canValidate ? "Yes" : "No";
            String status = workspace.get("status") != null ? workspace.get("status").toString() : "-";

            markdownBuilder.append(String.format("| %s | %s | %s | %s | %s |\n",
                    workspace.get("workspaceName"),
                    workspace.get("workspaceId"),
                    tempDbType,
                    validationStatus,
                    status));
        }

        return markdownBuilder.toString();
    }

    @Tool(
            name = "optimize_sql",
            description = """
                    Optimize SQL and return the optimization results in markdown format
                    """
    )
    public ApiResult optimizeSql(
            @Schema(description = "SQL query to be optimized", required = false)
            @ToolParam(required = true)
            String sql,

            @Schema(description = "Database type, can be null if not provided by user", required = false)
            @ToolParam(required = false)
            String dbType,

            @Schema(description = "Do not provide default values if not provided by user. Set to null if user doesn't provide DDL or database connection info. Note that database type is required when this is not null", required = false)
            @ToolParam(required = false)
            DatabaseInfo dbInfo,

            @Schema(description = "Whether to use workspace for optimization, based on dbInfo. True if user provides DDL info or database connection info, false otherwise", required = false)
            boolean useWorkspace,

            @Schema(description = "Existing workspace ID, true if provided by user, false otherwise", required = false)
            @ToolParam(required = false)
            String workspaceId,

            @Schema(description = "Whether to validate optimization results. Should be false unless user explicitly requests validation. Validation requires workspace with database connection info", required = false)
            boolean validateFlag
    ) {
        Thread currentThread = Thread.currentThread();
        log.debug("Optimizing SQL in thread {}: {}, useWorkspace: {}, validateFlag: {}", 
            currentThread.getName(), sql, useWorkspace, validateFlag);
        
        try {
            // Ensure we have a valid user context before proceeding
            // This will check thread-local, request context, and static session store
            // If no valid user context is found, it will throw SecurityException
            ensureUserContext();
            log.debug("User context verified for SQL optimization in thread: {}", currentThread.getName());
            
            // Validate parameters
            ApiResult validationResult = validateOptimizeParams(sql, dbType);
            if (validationResult != null) {
                return validationResult;
            }

            log.info("Starting SQL optimization, database type: {}, using workspace: {}", dbType, useWorkspace);
            String finalWorkspaceId = prepareWorkspace(workspaceId, useWorkspace, dbInfo);
            return processOptimization(sql, finalWorkspaceId, dbType, validateFlag);
        } catch (Exception e) {
            log.error("Error occurred during SQL optimization in thread {}", currentThread.getName(), e);
            return new ApiResult(500, "Error during SQL optimization: " + e.getMessage(), null);
        }
    }

    private ApiResult validateOptimizeParams(String sql, String dbType) {
        if (sql == null || sql.trim().isEmpty()) {
            return new ApiResult(400, "SQL statement cannot be empty. Please provide the SQL query to be optimized", null);
        }

        if (StringUtils.isBlank(dbType) || !isValidDbType(dbType)) {
            return new ApiResult(400, "Please provide a valid database type. Currently supported: MySQL(mysql), PostgreSQL(postgres), OpenGauss(opengauss), Oracle(oracle), Kingbase(kingbase), MogDB(opengauss), GaussDB Distributed(gaussdbx), GaussDB for DWS(dws),Terminate the conversation and wait for the user to provide a database type", null);
        }

        return null;
    }

    private String prepareWorkspace(String workspaceId, boolean useWorkspace, DatabaseInfo dbInfo) {
        if (StringUtils.isBlank(workspaceId) && useWorkspace && dbInfo != null) {
            workspaceId = apiService.createWorkspace(dbInfo);
            log.info("Workspace created: {}", workspaceId);
        }
        return workspaceId;
    }

    private ApiResult processOptimization(String sql, String workspaceId, String dbType, boolean validateFlag) {
        ApiResult createResult = apiService.createAnalysis(sql, workspaceId, dbType, validateFlag);
        if (createResult == null) {
            log.error("Failed to create SQL analysis task");
            return new ApiResult(500, "Failed to create SQL analysis task, please try again later", null);
        }

        Map<String, Object> data = (Map<String, Object>) createResult.data();
        String analysisId = (String) data.get("analysisId");
        log.info("Analysis task created, ID: {}", analysisId);

        ApiResult result = apiService.getAnalysisSummary(analysisId);
        return processAnalysisResult(result, workspaceId, analysisId);
    }

    private ApiResult processAnalysisResult(ApiResult result, String workspaceId, String analysisId) {
        Map<String, Object> summaryData = (Map<String, Object>) result.data();
        if (summaryData == null || summaryData.get("summaryStatementInfo") == null) {
            return result;
        }

        List<Map<String, Object>> stmtInfoList = (List<Map<String, Object>>) summaryData.get("summaryStatementInfo");
        if (stmtInfoList.isEmpty()) {
            return result;
        }

        String analysisStmtId = (String) stmtInfoList.get(0).get("analysisStmtId");
        ApiResult stmtDetails = apiService.getStatementDetails(analysisStmtId);

        if (stmtDetails != null && stmtDetails.data() != null) {
            Map<String, Object> detailsData = (Map<String, Object>) stmtDetails.data();
            Map<String, String> markdownParts = generateMarkdownReport(analysisStmtId, detailsData, workspaceId);
            return new ApiResult(result.code(), "Analysis report generated, including: 1. Report link 2. Analysis environment details 3. Optimization suggestions. This information will help you better understand and improve SQL query performance", markdownParts);
        }

        return result;
    }

    private Map<String, String> generateMarkdownReport(String analysisStmtId, Map<String, Object> detailsData, String workspaceId) {
        Map<String, String> markdownParts = new LinkedHashMap<>();
        String reportUrl = apiService.getFrontendUrl() + "/statement/" + analysisStmtId;

        // Part 1: Analysis report link
        markdownParts.put("reportLink", generateReportLink(reportUrl));

        // Part 2: Analysis environment details
        markdownParts.put("detail", (String) detailsData.getOrDefault("detailMarkdown", ""));

        // Part 3: Optimization suggestions
        markdownParts.put("suggestions", generateSuggestions(workspaceId));

        return markdownParts;
    }

    private String generateReportLink(String reportUrl) {
        return String.format("# SQL Optimization Analysis Report\n\n## 📊 Analysis Report\nView detailed analysis report: [Detailed Analysis Report](%s)\n", reportUrl);
    }
    
    /**
     * Generate optimization suggestions based on whether a workspace is provided
     * 
     * @param workspaceId The workspace ID, or null if no workspace is provided
     * @return A markdown string with optimization suggestions
     */
    private String generateSuggestions(String workspaceId) {
        StringBuilder suggestionsBuilder = new StringBuilder();
        if (workspaceId == null) {
            appendBasicSuggestions(suggestionsBuilder);
        } else {
            appendAdvancedSuggestions(suggestionsBuilder);
        }
        return suggestionsBuilder.toString();
    }

    /**
     * Append basic suggestions for users without a workspace
     * 
     * @param builder The StringBuilder to append to
     */
    private void appendBasicSuggestions(StringBuilder builder) {
        builder.append("\n## Methods to Improve SQL Optimization Analysis Accuracy\n")
                .append("To get more accurate SQL optimization suggestions, you can:\n\n")
                .append("### Method 1: Provide Table Structure Definitions\n")
                .append("Provide CREATE TABLE statements for relevant tables, and we will provide more accurate optimization suggestions based on the table structure.\n\n")
                .append("### Method 2: Use PawSQL Professional Platform\n")
                .append("Visit: " + apiService.getFrontendUrl() + "/app/workspaces\n")
                .append("On the professional platform, you can:\n")
                .append("• Create a validation workspace using database connection (recommended)\n")
                .append("  - Support optimization effect validation\n")
                .append("  - Provide visual execution plans\n")
                .append("  - Display detailed performance metrics\n")
                .append("• Create offline structure workspace by inputting DDL\n");
    }

    /**
     * Append advanced suggestions for users with a workspace
     * 
     * @param builder The StringBuilder to append to
     */
    private void appendAdvancedSuggestions(StringBuilder builder) {
        builder.append("\n## Further Improve Optimization Results\n")
                .append("You are already using a workspace for SQL optimization. To get more precise analysis results:\n\n")
                .append("### Upgrade to Validation Workspace\n")
                .append("Visit: " + apiService.getFrontendUrl() + "/app/workspaces\n")
                .append("By configuring database connection information, you will get:\n")
                .append("• Precise optimization suggestions based on real data distribution\n")
                .append("• Complete index usage and execution plan analysis\n");
    }

    /**
     * Check if the provided database type is valid
     * 
     * @param dbType The database type to check
     * @return true if valid, false otherwise
     */
    private boolean isValidDbType(String dbType) {
        if (StringUtils.isBlank(dbType)) {
            return false;
        }
        DefinitionEnum matchedType = DefinitionEnum.matchByDbType(dbType);
        return matchedType != null;
    }
    
    /**
     * Extracts a session ID from a thread name if it contains a UUID pattern
     * @param threadName The thread name to extract from
     * @return The extracted session ID or null if none found
     */
    private String extractSessionIdFromThreadName(String threadName) {
        if (threadName == null || threadName.isEmpty()) {
            return null;
        }
        
        // Look for UUID pattern in thread name (common format for session IDs)
        // UUID format: 8-4-4-4-12 hexadecimal digits
        Pattern uuidPattern = Pattern.compile(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", 
                Pattern.CASE_INSENSITIVE);
        Matcher matcher = uuidPattern.matcher(threadName);
        
        if (matcher.find()) {
            return matcher.group();
        }
        
        return null;
    }

    /**
     * Attempts to extract the MCP session ID from the current execution context.
     * This is used to identify the user session for background operations.
     * 
     * @return The MCP session ID, or null if not available
     */
    private String getMcpSessionId() {
        String threadName = Thread.currentThread().getName();
        
        try {
            // First check if we already have a user session in thread-local context
            // If so, we can use that session's ID directly
            if (threadLocalUserContext != null && threadLocalUserContext.hasUserSession()) {
                UserSession session = threadLocalUserContext.getUserSession();
                log.debug("Using existing thread-local user session ID: {} in thread: {}", 
                        session.getSessionId(), threadName);
                return session.getSessionId();
            }
            
            // Note: McpServerContext is not available in this project
            // We'll rely on other methods to get the session ID
            
            // Try to get session ID from thread name (if it contains a UUID pattern)
            String threadNameSessionId = extractSessionIdFromThreadName(threadName);
            if (threadNameSessionId != null) {
                log.debug("Extracted session ID from thread name: {} in thread: {}", 
                        threadNameSessionId, threadName);
                return threadNameSessionId;
            }
            
            // If still not found, try to get from request context if available
            try {
                org.springframework.web.context.request.RequestAttributes requestAttributes = 
                    org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
                    
                if (requestAttributes != null) {
                    HttpServletRequest request =
                        ((org.springframework.web.context.request.ServletRequestAttributes) requestAttributes).getRequest();
                    
                    if (request != null) {
                        // Try to get from headers
                        String authHeader = request.getHeader("X-MCP-Session-ID");
                        if (authHeader != null) {
                            log.debug("Extracted MCP session ID from request header: {}", authHeader);
                            return authHeader;
                        }
                        
                        // Try to get from parameters
                        String sessionParam = request.getParameter("sessionId");
                        if (sessionParam != null) {
                            log.debug("Extracted MCP session ID from request parameter: {}", sessionParam);
                            return sessionParam;
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Error extracting session ID from request context: {}", e.getMessage());
            }
            
            // Try to extract from thread name as a last resort
            if (threadName.contains("session-")) {
                String[] parts = threadName.split("session-");
                if (parts.length > 1) {
                    String possibleId = parts[1].split("[\\_\\-\\s]")[0]; // Extract until next delimiter
                    if (possibleId.length() > 8) { // Basic validation that it looks like a UUID fragment
                        log.debug("Extracted possible MCP session ID from thread name: {} in thread: {}", 
                                possibleId, threadName);
                        return possibleId;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Error in getMcpSessionId: {}", e.getMessage());
        }
        
        log.debug("Could not determine MCP session ID in thread: {}", threadName);
        return null;
    }

    /**
     * Tries to set the user session from the MCP session ID.
     * This method attempts to retrieve the user session from the authentication service using the MCP session ID.
     * 
     * @param sessionId The MCP session ID
     */
    private void trySetUserSessionFromMcp(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            log.debug("Cannot set user session: MCP session ID is null or empty");
            return;
        }
        
        try {
            // First check if we already have a user session in thread-local context
            if (threadLocalUserContext != null && threadLocalUserContext.hasUserSession()) {
                UserSession existingSession = threadLocalUserContext.getUserSession();
                log.debug("Thread already has user session: {}", existingSession.getEmail());
                return; // Already have a session, no need to set it again
            }
            
            // Try to get the user session from the authentication service
            Optional<UserSession> userSession = authenticationService.validateSession(sessionId);
            
            if (userSession.isPresent()) {
                // Set the user session in thread-local context
                threadLocalUserContext.setUserSession(userSession.get());
                
                // Also set it for the API service
                apiService.setCurrentUserSession(userSession.get());
                
                log.debug("Set user session from MCP session ID: {}, user: {}, thread: {}", 
                    sessionId, userSession.get().getEmail(), Thread.currentThread().getName());
            } else {
                log.warn("No valid user session found for MCP session ID: {}", sessionId);
            }
        } catch (Exception e) {
            log.warn("Failed to set user session from MCP session ID: {}", e.getMessage());
        }
    }

    /**
     * Ensures that a valid user context is available for the current operation.
     * First checks the thread-local storage, then falls back to the request-scoped UserContextManager.
     * Throws an exception if no user context is available.
     */
    private void ensureUserContext() {
        UserSession session = null;
        String threadName = Thread.currentThread().getName();
        
        // First try to get from thread-local context
        if (threadLocalUserContext.hasUserSession()) {
            session = threadLocalUserContext.getUserSession();
            log.debug("Using thread-local user session: {} in thread: {}", session.getEmail(), threadName);
        } else {
            // If not in thread-local, try to get from request context
            // But only if we're in a request thread (avoid accessing request-scoped beans in background threads)
            boolean isRequestThread = false;
            try {
                // Check if we're in a request thread by checking if RequestContextHolder has request attributes
                isRequestThread = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes() != null;
            } catch (Exception e) {
                log.debug("Error checking request thread status: {}", e.getMessage());
            }
            
            if (isRequestThread) {
                try {
                    if (userContextManager.hasUserSession()) {
                        session = userContextManager.getCurrentUserSession();
                        // Store in thread-local for future use in this thread
                        threadLocalUserContext.setUserSession(session);
                        log.debug("Copied request-scoped user session to thread-local: {} in thread: {}", 
                                session.getEmail(), threadName);
                    }
                } catch (Exception e) {
                    log.debug("Could not access request-scoped UserContextManager: {}", e.getMessage());
                }
            } else {
                log.debug("Not in a request thread, skipping request-scoped UserContextManager access in thread: {}", threadName);
            }
            
            // If still no session, try to get from MCP context
            if (session == null) {
                String mcpSessionId = getMcpSessionId();
                if (mcpSessionId != null && !mcpSessionId.isEmpty()) {
                    // Try to get from static session store using MCP session ID
                    session = sessionStore.get(mcpSessionId);
                    if (session != null) {
                        log.debug("Retrieved user session from static session store using MCP session ID: {} in thread: {}", 
                                mcpSessionId, threadName);
                        // Store in thread-local for future use
                        threadLocalUserContext.setUserSession(session);
                    } else {
                        log.warn("MCP session ID {} found but no corresponding user session in static store in thread: {}", 
                                mcpSessionId, threadName);
                    }
                } else {
                    log.warn("No MCP session ID found in thread: {}", threadName);
                }
            }
        }
        
        if (session == null) {
            throw new SecurityException("No authenticated user context available for this operation in thread: " + threadName);
        }
        
        apiService.setCurrentUserSession(session);
    }
}

