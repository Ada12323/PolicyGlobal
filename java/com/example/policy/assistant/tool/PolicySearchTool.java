package com.example.policy.assistant.tool;

import com.example.policy.assistant.service.SqlExecutionService;
import com.example.policy.assistant.dto.SqlQueryResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class PolicySearchTool implements Tool {

    private final SqlExecutionService sqlExecutionService;
    private final ObjectMapper mapper = new ObjectMapper();

    public PolicySearchTool(SqlExecutionService sqlExecutionService) {
        this.sqlExecutionService = sqlExecutionService;
    }

    @Override
    public String getName() {
        return "PolicySearchTool";
    }

    @Override
    public ToolDefinition getDefinition() {
        return new ToolDefinition(
            getName(),
            "用于根据关键词、日期范围、发布机构搜索政策信息。如果数据库字段不完全匹配，优先使用关键词模糊查询。",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "keyword", Map.of("type", "string", "description", "搜索关键词"),
                    "startDate", Map.of("type", "string", "description", "开始日期，格式 YYYY-MM-DD"),
                    "endDate", Map.of("type", "string", "description", "结束日期，格式 YYYY-MM-DD"),
                    "orgName", Map.of("type", "string", "description", "发布机构名称")
                ),
                "required", List.of("keyword")
            )
        );
    }

    @Override
    public String execute(String argumentsJson) {
        try {
            JsonNode args = mapper.readTree(argumentsJson);
            String keyword = args.path("keyword").asText(null);
            String startDate = args.path("startDate").asText(null);
            String endDate = args.path("endDate").asText(null);
            String orgName = args.path("orgName").asText(null);

            StringBuilder sql = new StringBuilder(
                "SELECT d.id, d.zh_title, d.summary, d.publish_date, d.source_url, o.org_name " +
                "FROM policy_data d " +
                "LEFT JOIN policy_og o ON d.org_id = o.id WHERE 1=1"
            );

            if (keyword != null && !keyword.isBlank() && !keyword.equals("null")) {
                String safeKeyword = keyword.replace("'", "''");
                sql.append(" AND (d.zh_title LIKE '%").append(safeKeyword).append("%' OR d.summary LIKE '%").append(safeKeyword).append("%')");
            }
            if (startDate != null && !startDate.isBlank() && !startDate.equals("null")) {
                sql.append(" AND d.publish_date >= '").append(startDate.replace("'", "")).append("'");
            }
            if (endDate != null && !endDate.isBlank() && !endDate.equals("null")) {
                sql.append(" AND d.publish_date <= '").append(endDate.replace("'", "")).append("'");
            }
            if (orgName != null && !orgName.isBlank() && !orgName.equals("null")) {
                sql.append(" AND o.org_name LIKE '%").append(orgName.replace("'", "''")).append("%'");
            }
            sql.append(" ORDER BY d.publish_date DESC LIMIT 10");

            SqlQueryResult result = sqlExecutionService.execute(sql.toString());
            return mapper.writeValueAsString(result.getRows());
        } catch (Exception e) {
            return "Error executing PolicySearchTool: " + e.getMessage();
        }
    }
}
