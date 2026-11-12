package com.example.policy.assistant.tool;

import com.example.policy.assistant.service.SqlExecutionService;
import com.example.policy.assistant.dto.SqlQueryResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class LatestPolicyTool implements Tool {
    private final SqlExecutionService sqlExecutionService;
    private final ObjectMapper mapper = new ObjectMapper();

    public LatestPolicyTool(SqlExecutionService sqlExecutionService) {
        this.sqlExecutionService = sqlExecutionService;
    }

    @Override
    public String getName() {
        return "LatestPolicyTool";
    }

    @Override
    public ToolDefinition getDefinition() {
        return new ToolDefinition(
            getName(),
            "查询最新发布的政策。",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "keyword", Map.of("type", "string", "description", "可选，搜索关键词")
                )
            )
        );
    }

    @Override
    public String execute(String argumentsJson) {
        try {
            JsonNode args = mapper.readTree(argumentsJson);
            String keyword = args.path("keyword").asText(null);

            StringBuilder sql = new StringBuilder(
                "SELECT d.id, d.zh_title, d.summary, d.publish_date, d.source_url, o.org_name " +
                "FROM policy_data d " +
                "LEFT JOIN policy_og o ON d.org_id = o.id WHERE 1=1"
            );

            if (keyword != null && !keyword.isBlank() && !keyword.equals("null")) {
                String safeKeyword = keyword.replace("'", "''");
                sql.append(" AND (d.zh_title LIKE '%").append(safeKeyword).append("%' OR d.summary LIKE '%").append(safeKeyword).append("%')");
            }
            sql.append(" ORDER BY d.publish_date DESC LIMIT 5");

            SqlQueryResult result = sqlExecutionService.execute(sql.toString());
            return mapper.writeValueAsString(result.getRows());
        } catch (Exception e) {
            return "Error executing LatestPolicyTool: " + e.getMessage();
        }
    }
}
