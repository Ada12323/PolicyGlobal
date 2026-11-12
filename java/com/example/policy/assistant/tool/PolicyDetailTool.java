package com.example.policy.assistant.tool;

import com.example.policy.assistant.service.SqlExecutionService;
import com.example.policy.assistant.dto.SqlQueryResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class PolicyDetailTool implements Tool {
    private final SqlExecutionService sqlExecutionService;
    private final ObjectMapper mapper = new ObjectMapper();

    public PolicyDetailTool(SqlExecutionService sqlExecutionService) {
        this.sqlExecutionService = sqlExecutionService;
    }

    @Override
    public String getName() {
        return "PolicyDetailTool";
    }

    @Override
    public ToolDefinition getDefinition() {
        return new ToolDefinition(
            getName(),
            "查询某条政策的详细信息",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "policyId", Map.of("type", "string", "description", "政策ID")
                ),
                "required", List.of("policyId")
            )
        );
    }

    @Override
    public String execute(String argumentsJson) {
        try {
            JsonNode args = mapper.readTree(argumentsJson);
            String policyId = args.path("policyId").asText(null);
            
            if (policyId == null || policyId.isBlank() || policyId.equals("null")) {
                return "Error: policyId is required";
            }

            String sql = "SELECT d.*, o.org_name FROM policy_data d " +
                         "LEFT JOIN policy_og o ON d.org_id = o.id " +
                         "WHERE d.id = '" + policyId.replace("'", "''") + "'";

            SqlQueryResult result = sqlExecutionService.execute(sql);
            return mapper.writeValueAsString(result.getRows());
        } catch (Exception e) {
            return "Error executing PolicyDetailTool: " + e.getMessage();
        }
    }
}
