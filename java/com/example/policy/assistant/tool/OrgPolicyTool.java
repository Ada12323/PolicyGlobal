package com.example.policy.assistant.tool;

import com.example.policy.assistant.service.SqlExecutionService;
import com.example.policy.assistant.dto.SqlQueryResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class OrgPolicyTool implements Tool {
    private final SqlExecutionService sqlExecutionService;
    private final ObjectMapper mapper = new ObjectMapper();

    public OrgPolicyTool(SqlExecutionService sqlExecutionService) {
        this.sqlExecutionService = sqlExecutionService;
    }

    @Override
    public String getName() {
        return "OrgPolicyTool";
    }

    @Override
    public ToolDefinition getDefinition() {
        return new ToolDefinition(
            getName(),
            "查询某机构发布的政策",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "orgName", Map.of("type", "string", "description", "发布机构名称")
                ),
                "required", List.of("orgName")
            )
        );
    }

    @Override
    public String execute(String argumentsJson) {
        try {
            JsonNode args = mapper.readTree(argumentsJson);
            String orgName = args.path("orgName").asText(null);
            
            if (orgName == null || orgName.isBlank() || orgName.equals("null")) {
                return "Error: orgName is required";
            }

            String sql = "SELECT d.id, d.zh_title, d.summary, d.publish_date, d.source_url, o.org_name " +
                         "FROM policy_data d " +
                         "JOIN policy_og o ON d.org_id = o.id " +
                         "WHERE o.org_name LIKE '%" + orgName.replace("'", "''") + "%' " +
                         "ORDER BY d.publish_date DESC LIMIT 10";

            SqlQueryResult result = sqlExecutionService.execute(sql);
            return mapper.writeValueAsString(result.getRows());
        } catch (Exception e) {
            return "Error executing OrgPolicyTool: " + e.getMessage();
        }
    }
}
