package com.pawsql.mcp;

import com.pawsql.mcp.service.SqlOptimizeService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class PawSQLMCPApplication {

    public static void main(String[] args) {
        SpringApplication.run(PawSQLMCPApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider pawsqlTools(SqlOptimizeService sqlOptimizeService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(sqlOptimizeService)
                .build();
    }


}
