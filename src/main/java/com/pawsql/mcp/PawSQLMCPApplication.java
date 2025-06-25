package com.pawsql.mcp;

import com.pawsql.mcp.service.SqlOptimizeService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PawSQLMCPApplication {

    public static void main(String[] args) {
        SpringApplication.run(PawSQLMCPApplication.class, args);
    }

    /**
     * Registers the SqlOptimizeService as a tool for the MCP server.
     */
    @Bean
    public ToolCallbackProvider pawsqlTools(SqlOptimizeService sqlOptimizeService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(sqlOptimizeService)
                .build();
    }
}
