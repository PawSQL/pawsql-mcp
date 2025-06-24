package com.pawsql.mcp;

import com.pawsql.mcp.service.SqlOptimizeService;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.List;

@SpringBootApplication
public class PawSQLMCPApplication {

    public static void main(String[] args) {
        SpringApplication.run(PawSQLMCPApplication.class, args);
    }

    @Bean
    public List<ToolCallback> pawsqlTools(SqlOptimizeService sqlOptimizeService) {
        return List.of(ToolCallbacks.from(sqlOptimizeService));
    }


}
