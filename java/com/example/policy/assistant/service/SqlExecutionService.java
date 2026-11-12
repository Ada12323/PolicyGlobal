package com.example.policy.assistant.service;

import com.example.policy.assistant.config.AssistantProperties;
import com.example.policy.assistant.dto.SqlQueryResult;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.ResultSetMetaData;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SqlExecutionService {

    private static final Logger log = LoggerFactory.getLogger(SqlExecutionService.class);

    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
        "insert", "update", "delete", "drop", "alter", "create", "truncate", "merge", "call", "grant", "revoke"
    );

    private static final Set<String> ALLOWED_TABLES = Set.of("policy_data", "policy_og");
    private static final Pattern TABLE_PATTERN = Pattern.compile("(?i)\\b(from|join)\\s+([a-zA-Z0-9_`\\.]+)(?:\\s+as)?(?:\\s+[a-zA-Z0-9_]+)?");

    private final JdbcTemplate jdbcTemplate;
    private final AssistantProperties assistantProperties;
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    public SqlExecutionService(JdbcTemplate jdbcTemplate, AssistantProperties assistantProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.assistantProperties = assistantProperties;
    }

    public SqlQueryResult execute(String rawSql) {
        if (!StringUtils.hasText(rawSql)) {
            return SqlQueryResult.emptyWithWarning("模型未返回有效 SQL");
        }
        String sql = normalizeWhitespace(rawSql);
        sql = stripTrailingSemicolon(sql);
        if (!sql.toLowerCase().startsWith("select")) {
            throw new IllegalArgumentException("仅允许执行 SELECT 查询");
        }
        if (!assistantProperties.getSql().isAllowWrite()) {
            ensureNoForbiddenKeyword(sql);
        }
        ensureNoSqlCommentsOrMultiStatements(sql);
        ensureAllowedTables(sql);

        boolean appendedLimit = false;
        int maxLimit = Math.max(1, assistantProperties.getSql().getMaxLimit());
        if (!sql.toLowerCase().contains(" limit ")) {
            sql = sql + " LIMIT " + maxLimit;
            appendedLimit = true;
        }

        final String executableSql = sql;
        Future<List<Map<String, Object>>> future = executorService.submit(() -> runQuery(executableSql, maxLimit));
        try {
            List<Map<String, Object>> rows = future.get(assistantProperties.getSql().getTimeoutSeconds(), TimeUnit.SECONDS);
            List<String> warnings = appendedLimit ? Collections.singletonList("已自动限制返回行数至 " + maxLimit + " 行") : Collections.emptyList();
            return new SqlQueryResult(rows, warnings);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("SQL 执行超时: {}", executableSql);
            throw new RuntimeException("SQL 执行超时，请缩小查询范围");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("SQL 执行被中断");
        } catch (ExecutionException e) {
            throw new RuntimeException("SQL 执行失败: " + e.getCause().getMessage(), e.getCause());
        }
    }

    private List<Map<String, Object>> runQuery(String sql, int maxLimit) {
        log.debug("Executing AI generated SQL: {}", sql);
        return jdbcTemplate.query(con -> {
            PreparedStatement ps = con.prepareStatement(sql);
            ps.setMaxRows(maxLimit);
            return ps;
        }, rs -> {
            List<Map<String, Object>> rows = new ArrayList<>();
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    String column = metaData.getColumnLabel(i);
                    Object value = JdbcUtils.getResultSetValue(rs, i);
                    row.put(column, normalizeValue(value));
                }
                rows.add(row);
            }
            return rows;
        });
    }

    private Object normalizeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        }
        return value;
    }

    private void ensureNoForbiddenKeyword(String sql) {
        String lower = sql.toLowerCase();
        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (lower.contains(keyword)) {
                throw new IllegalArgumentException("SQL 包含禁止关键字: " + keyword);
            }
        }
    }

    private void ensureNoSqlCommentsOrMultiStatements(String sql) {
        String lower = sql.toLowerCase();
        if (lower.contains(";") || lower.contains("--") || lower.contains("/*") || lower.contains("*/")) {
            throw new IllegalArgumentException("SQL 语句包含非法分号或注释");
        }
    }

    private String stripTrailingSemicolon(String sql) {
        String trimmed = sql.trim();
        if (!trimmed.endsWith(";")) {
            return trimmed;
        }
        String withoutTrailing = trimmed.substring(0, trimmed.length() - 1).trim();
        if (withoutTrailing.contains(";")) {
            throw new IllegalArgumentException("SQL 语句包含非法分号或注释");
        }
        return withoutTrailing;
    }

    private void ensureAllowedTables(String sql) {
        Matcher matcher = TABLE_PATTERN.matcher(sql);
        boolean hasAny = false;
        while (matcher.find()) {
            hasAny = true;
            String raw = matcher.group(2);
            if (raw == null) {
                continue;
            }
            String table = raw.replace("`", "");
            if (table.contains(".")) {
                table = table.substring(table.lastIndexOf('.') + 1);
            }
            table = table.trim().split(" ")[0];
            if (!ALLOWED_TABLES.contains(table.toLowerCase())) {
                throw new IllegalArgumentException("检测到未授权的表访问: " + table);
            }
        }
        if (!hasAny) {
            throw new IllegalArgumentException("仅允许访问 policy_data 与 policy_og 表");
        }
    }

    private String normalizeWhitespace(String sql) {
        return sql.trim().replaceAll("\\s+", " ");
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdownNow();
    }
}
