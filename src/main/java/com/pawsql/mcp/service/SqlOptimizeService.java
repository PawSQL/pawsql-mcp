package com.pawsql.mcp.service;

import com.pawsql.mcp.context.PawSQLAuthContext;
import com.pawsql.mcp.context.RequestContextManager;
import com.pawsql.mcp.enums.DefinitionEnum;
import com.pawsql.mcp.model.ApiResult;
import com.pawsql.mcp.model.DatabaseInfo;
import io.micrometer.common.util.StringUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SqlOptimizeService {
    private static final Logger log = LoggerFactory.getLogger(SqlOptimizeService.class);
    private final PawsqlApiService apiService;

    @Autowired
    private RequestContextManager requestContextManager;

    @Value("${pawsql.anonymous.user-key:}")
    private String anonymousUserKey;

    public SqlOptimizeService(PawsqlApiService apiService) {
        this.apiService = apiService;
    }

    /**
     * 确保API服务初始化
     * 在非Web请求线程中调用API服务前，需要手动初始化认证信息
     * 使用响应式编程模型的Reactor Context机制传递认证信息
     */
    private void ensureApiServiceInitialized() {
        try {
            // 尝试获取APIKEY，如果返回null，说明未初始化
            if (apiService.getApiKey() == null) {
                log.info("API服务未初始化，从传统上下文中获取");

                // 从传统的RequestContextManager中获取认证信息
                PawSQLAuthContext legacyAuthContext = PawSQLAuthContext.fromRequestContext(requestContextManager);

                if (legacyAuthContext == null) {
                    // 没有认证上下文，尝试用匿名 key 兜底
                    if (anonymousUserKey != null && !anonymousUserKey.isEmpty()) {
                        log.info("无认证上下文，使用匿名key初始化");
                        String baseUrl = apiService.getApiBaseUrl();
                        // 先用 baseUrl 作为临时 frontendUrl，再从后端 /system/info 获取真实值
                        apiService.setAuthInfo(baseUrl, baseUrl, null, anonymousUserKey);
                        String fetchedFrontendUrl = apiService.fetchFrontendUrl();
                        if (fetchedFrontendUrl != null && !fetchedFrontendUrl.isEmpty()) {
                            apiService.setAuthInfo(baseUrl, fetchedFrontendUrl, null, anonymousUserKey);
                        }
                        return;
                    }
                    log.error("无法获取认证信息，请确保在调用前已设置认证上下文");
                    throw new IllegalStateException("认证信息缺失，请确保在调用前已设置认证上下文。认证信息必须从请求头中获取");
                }

                // 验证传统认证信息是否完整
                if (legacyAuthContext.getApiKey() == null) {
                    log.error("认证信息不完整，缺失必要字段");
                    throw new IllegalStateException("认证信息不完整，请确保请求头中包含所有必要的认证字段");
                }

                // 使用传统认证上下文初始化API服务
                requestContextManager.runWithAuth(legacyAuthContext.getTokenPayload(), () -> {
                    String baseUrl = requestContextManager.getBaseUrl();
                    String frontendUrl = requestContextManager.getFrontendUrl();
                    String version = requestContextManager.getVersion();
                    String apiKey = requestContextManager.getApiKey();

                    apiService.setAuthInfo(baseUrl, frontendUrl, version, apiKey);
                });
            }
        } catch (Exception e) {
            log.error("API服务初始化失败", e);
            throw new IllegalStateException("无法初始化API服务：" + e.getMessage(), e);
        }
    }

    @Tool(
            name = "list_workspaces",
            description = "List current workspaces and return their basic information in a markdown table. " +
                    "Note: This tool requires a registered user key. Anonymous users cannot list workspaces."
    )
    public ApiResult listWorkspaces() {
        try {
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
            description = "Get workspace information by name or ID, including ID, database type, etc. " +
                    "Returns a notification if the workspace is not found. " +
                    "Note: This tool requires a registered user key. Anonymous users cannot query workspace info."
    )
    public ApiResult getWorkspaceInfo(
            @Schema(description = "Workspace name", required = false)
            @ToolParam(required = false)
            String workspaceName,
            @Schema(description = "Workspace ID", required = false)
            @ToolParam(required = false)
            String workspaceId
    ) {
        try {
            // 确保API服务已初始化
            ensureApiServiceInitialized();

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
            // 确保API服务已初始化
            ensureApiServiceInitialized();

            ApiResult workspaces = apiService.listWorkspaces(1, 10);
            if (workspaces == null || workspaces.data() == null) {
                return null;
            }

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
            @ToolParam(required = false)
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
        ApiResult validationResult = validateOptimizeParams(sql, dbType);
        if (validationResult != null) return validationResult;

        try {
            log.info("Starting SQL optimization, database type: {}, using workspace: {}", dbType, useWorkspace);
            String finalWorkspaceId = prepareWorkspace(workspaceId, useWorkspace, dbInfo);
            return processOptimization(sql, finalWorkspaceId, dbType, validateFlag);
        } catch (Exception e) {
            log.error("Error occurred during SQL optimization", e);
            return new ApiResult(500, "Error during SQL optimization: " + e.getMessage(), null);
        }
    }

    @Tool(
            name = "check_optimization_status",
            description = "Check the status of a SQL optimization task and return results if completed. " +
                    "Use this after optimize_sql returns an 'in progress' notice. " +
                    "You can query by analysisId or analysisName (at least one is required)."
    )
    public ApiResult checkOptimizationStatus(
            @Schema(description = "Analysis ID returned by optimize_sql", required = false)
            @ToolParam(required = false)
            String analysisId,

            @Schema(description = "Analysis name (used when analysisId is not provided)", required = false)
            @ToolParam(required = false)
            String analysisName,

            @Schema(description = "Workspace ID used during optimization", required = false)
            @ToolParam(required = false)
            String workspaceId
    ) {
        if (StringUtils.isBlank(analysisId) && StringUtils.isBlank(analysisName)) {
            return new ApiResult(400, "analysisId and analysisName cannot both be empty", null);
        }
        try {
            ensureApiServiceInitialized();
            String queryDesc = StringUtils.isNotBlank(analysisId) ? "analysisId=" + analysisId : "analysisName=" + analysisName;
            log.info("Checking optimization status for {}", queryDesc);

            ApiResult summaryResult = apiService.getAnalysisSummary(analysisId, analysisName);
            if (summaryResult == null || summaryResult.data() == null) {
                return new ApiResult(500, "Failed to query analysis status", null);
            }

            Map<String, Object> summaryData = (Map<String, Object>) summaryResult.data();
            String status = summaryData.get("status") != null ? summaryData.get("status").toString() : "";
            String resolvedAnalysisId = summaryData.get("analysisId") != null ? summaryData.get("analysisId").toString() : analysisId;
            log.info("Analysis {} status: {}", queryDesc, status);

            if ("failed".equals(status)) {
                return new ApiResult(500, "SQL optimization failed on the server side", null);
            }
            if (status.startsWith("success")) {
                return processAnalysisResult(summaryResult, workspaceId, resolvedAnalysisId);
            }
            // still running
            String analysesUrl = apiService.getFrontendUrl() + "/app/analyses";
            Map<String, String> notice = new LinkedHashMap<>();
            notice.put("analysisId", resolvedAnalysisId);
            notice.put("status", status);
            notice.put("analysesUrl", analysesUrl);
            notice.put("message", "SQL optimization is still in progress. Please check again later at " + analysesUrl +
                    " or call check_optimization_status again with analysisId=" + resolvedAnalysisId + ".");
            return new ApiResult(200, "Optimization in progress", notice);

        } catch (Exception e) {
            log.error("Failed to check optimization status", e);
            return new ApiResult(500, "Failed to check optimization status: " + e.getMessage(), null);
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

    private static final long OPTIMIZATION_TIMEOUT_SECONDS = 120;
    private static final long POLL_INTERVAL_SECONDS = 3;

    private ApiResult processOptimization(String sql, String workspaceId, String dbType, boolean validateFlag) {
        try {
            // 确保API服务已初始化
            ensureApiServiceInitialized();

            ApiResult createResult = apiService.createAnalysis(sql, workspaceId, dbType, validateFlag);
            if (createResult == null) {
                log.error("Failed to create SQL analysis task");
                return new ApiResult(500, "Failed to create SQL analysis task, please try again later", null);
            }

            Map<String, Object> data = (Map<String, Object>) createResult.data();
            String analysisId = (String) data.get("analysisId");
            String status = data.get("status") != null ? data.get("status").toString() : "";
            log.info("Analysis task created, ID: {}, status: {}", analysisId, status);

            // 如果创建时就已经完成（命中缓存等），直接获取结果
            if ("success".equals(status) || status.startsWith("success")) {
                ApiResult result = apiService.getAnalysisSummary(analysisId);
                return processAnalysisResult(result, workspaceId, analysisId);
            }

            // 轮询等待优化完成，最多等 2 分钟
            long deadline = System.currentTimeMillis() + OPTIMIZATION_TIMEOUT_SECONDS * 1000;
            while (System.currentTimeMillis() < deadline) {
                Thread.sleep(POLL_INTERVAL_SECONDS * 1000);
                ApiResult summaryResult = apiService.getAnalysisSummary(analysisId);
                if (summaryResult == null || summaryResult.data() == null) {
                    continue;
                }
                Map<String, Object> summaryData = (Map<String, Object>) summaryResult.data();
                String currentStatus = summaryData.get("status") != null ? summaryData.get("status").toString() : "";
                log.info("Analysis {} status: {}", analysisId, currentStatus);

                if ("failed".equals(currentStatus)) {
                    return new ApiResult(500, "SQL optimization failed on the server side", null);
                }
                if (currentStatus.startsWith("success")) {
                    return processAnalysisResult(summaryResult, workspaceId, analysisId);
                }
                // status=running，继续轮询
            }

            // 超时，返回提示
            log.info("Analysis {} still running after {}s, returning async notice", analysisId, OPTIMIZATION_TIMEOUT_SECONDS);
            String analysesUrl = apiService.getFrontendUrl() + "/app/analyses";
            Map<String, String> notice = new LinkedHashMap<>();
            notice.put("analysisId", analysisId);
            notice.put("analysesUrl", analysesUrl);
            notice.put("message", "SQL optimization is still in progress (it may take several minutes for complex SQL). " +
                    "You can check the optimization status at " + analysesUrl + ". " +
                    "Once completed, provide the analysis name or ID to continue the discussion, " +
                    "or call check_optimization_status with analysisId=" + analysisId + " to query the result.");
            return new ApiResult(200, "SQL optimization is in progress", notice);

        } catch (Exception e) {
            log.error("SQL optimization failed", e);
            return new ApiResult(500, "SQL optimization failed: " + e.getMessage(), null);
        }
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

    private String generateSuggestions(String workspaceId) {
        StringBuilder suggestionsBuilder = new StringBuilder();
        if (workspaceId == null) {
            appendBasicSuggestions(suggestionsBuilder);
        } else {
            appendAdvancedSuggestions(suggestionsBuilder);
        }
        return suggestionsBuilder.toString();
    }

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

    private void appendAdvancedSuggestions(StringBuilder builder) {
        builder.append("\n## Further Improve Optimization Results\n")
                .append("You are already using a workspace for SQL optimization. To get more precise analysis results:\n\n")
                .append("### Upgrade to Validation Workspace\n")
                .append("Visit: " + apiService.getFrontendUrl() + "/app/workspaces\n")
                .append("By configuring database connection information, you will get:\n")
                .append("• Precise optimization suggestions based on real data distribution\n")
                .append("• Complete index usage and execution plan analysis\n");
    }

    private boolean isValidDbType(String dbType) {
        if (StringUtils.isBlank(dbType)) {
            return false;
        }
        DefinitionEnum matchedType = DefinitionEnum.matchByDbType(dbType);
        return matchedType != null;
    }
}

