package com.pawsql.mcp.model;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

/**
 * Database information class for SQL optimization in offline mode
 * <p>
 * Offline mode: Only requires table DDL statements and database type
 */
@Schema(description = "Database information for SQL optimization in offline mode")
public class DatabaseInfo {
    /**
     * Database type
     */
    @Schema(description = "Database type", required = true)
    private String dbType;

    /**
     * Table DDL statements
     */
    @Schema(description = "Table DDL statements", required = true)
    private String ddlText;

    public DatabaseInfo() {
    }

    /**
     * Create a database information instance
     *
     * @param ddlText Table DDL statements
     * @param dbType  Database type
     * @return DatabaseInfo instance
     */
    public static DatabaseInfo of(String ddlText, String dbType) {
        DatabaseInfo info = new DatabaseInfo();
        info.setDdlText(ddlText);
        info.setDbType(dbType);
        return info;
    }

    public String getDbType() {
        return dbType;
    }

    public void setDbType(String dbType) {
        this.dbType = dbType;
    }

    public String getDdlText() {
        return ddlText;
    }

    public void setDdlText(String ddlText) {
        this.ddlText = ddlText;
    }
}