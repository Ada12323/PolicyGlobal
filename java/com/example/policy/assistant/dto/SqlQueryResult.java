package com.example.policy.assistant.dto;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SqlQueryResult {

    private List<Map<String, Object>> rows;
    private List<String> warnings;

    public SqlQueryResult() {
    }

    public SqlQueryResult(List<Map<String, Object>> rows, List<String> warnings) {
        this.rows = rows;
        this.warnings = warnings;
    }

    public List<Map<String, Object>> getRows() {
        return rows;
    }

    public void setRows(List<Map<String, Object>> rows) {
        this.rows = rows;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public static SqlQueryResult emptyWithWarning(String warning) {
        return new SqlQueryResult(Collections.emptyList(), Collections.singletonList(warning));
    }
}
