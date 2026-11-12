package com.example.policy.assistant.client;

import com.example.policy.assistant.dto.ChatCompletionResponse;
import com.example.policy.assistant.dto.ToolCall;
import com.example.policy.assistant.tool.ToolDefinition;
import com.example.policy.assistant.config.QwenProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.function.Consumer;

@Component
public class QwenClient {

    private static final Logger log = LoggerFactory.getLogger(QwenClient.class);
    private static final int API_MAX_TOKENS = 16384;
    private static final int API_MIN_TOKENS = 1;

    private final RestTemplate restTemplate;
    private final QwenProperties properties;
    private final ObjectMapper mapper = new ObjectMapper();
    private final int defaultTimeoutMs;

    public QwenClient(QwenProperties properties,
                      @Value("${llm.timeout-seconds:120}") int timeoutSeconds) {
        this.properties = properties;
        int timeout = Math.max(timeoutSeconds, 30);
        this.defaultTimeoutMs = timeout * 1000;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(defaultTimeoutMs);
        factory.setReadTimeout(defaultTimeoutMs);
        this.restTemplate = new RestTemplate(factory);
        this.restTemplate.getMessageConverters().removeIf(conv -> conv instanceof org.springframework.http.converter.StringHttpMessageConverter);
        this.restTemplate.getMessageConverters().add(0, new org.springframework.http.converter.StringHttpMessageConverter(StandardCharsets.UTF_8));
    }

    public ChatCompletionResponse completeWithTools(List<Map<String, Object>> messages, List<ToolDefinition> tools, Integer maxTokensOverride) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("Qwen API key is not configured");
        }
        if (CollectionUtils.isEmpty(messages)) {
            throw new IllegalArgumentException("messages must not be empty");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", properties.getModel());
        payload.put("messages", messages);
        if (properties.getTemperature() != null) {
            payload.put("temperature", properties.getTemperature());
        }
        Integer desiredTokens = maxTokensOverride != null ? maxTokensOverride : properties.getMaxTokens();
        if (desiredTokens != null) {
            int safeTokens = Math.max(API_MIN_TOKENS, Math.min(API_MAX_TOKENS, desiredTokens));
            payload.put("max_tokens", safeTokens);
        }
        payload.put("stream", false);
        
        if (tools != null && !tools.isEmpty()) {
            payload.put("tools", tools);
            payload.put("tool_choice", "auto");
        }
        
        boolean enableThinking = Boolean.TRUE.equals(properties.getEnableThinking());
        if (enableThinking) {
            log.warn("[Qwen] enableThinking=true 但当前为非流式请求，已自动置为 false 以避免 API 报错");
            enableThinking = false;
        }
        payload.put("enable_thinking", enableThinking);
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(normalizeKey(properties.getApiKey()));
            HttpEntity<String> entity = new HttpEntity<>(mapper.writeValueAsString(payload), headers);
            ResponseEntity<String> response = restTemplate.postForEntity(properties.getBaseUrl(), entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IllegalStateException("Qwen API error: " + response.getStatusCode());
            }
            JsonNode root = mapper.readTree(response.getBody());
            JsonNode messageNode = root.at("/choices/0/message");
            String content = messageNode.path("content").asText(null);
            
            List<ToolCall> toolCalls = null;
            JsonNode toolCallsNode = messageNode.path("tool_calls");
            if (toolCallsNode.isArray() && !toolCallsNode.isEmpty()) {
                toolCalls = mapper.convertValue(toolCallsNode, new com.fasterxml.jackson.core.type.TypeReference<List<ToolCall>>(){});
            }
            
            return new ChatCompletionResponse(content, toolCalls);
        } catch (Exception e) {
            log.error("Qwen completion failed: {}", e.getMessage());
            throw new RuntimeException("调用策略分析模型失败: " + e.getMessage(), e);
        }
    }

    public void streamWithTools(List<Map<String, Object>> messages,
                       List<ToolDefinition> tools,
                       Integer maxTokensOverride,
                       Consumer<ChatCompletionResponse> onDelta,
                       Runnable onDone,
                       Consumer<Throwable> onError) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("Qwen API key is not configured");
        }
        if (CollectionUtils.isEmpty(messages)) {
            throw new IllegalArgumentException("messages must not be empty");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", properties.getModel());
        payload.put("messages", messages);
        Integer desiredTokens = maxTokensOverride != null ? maxTokensOverride : properties.getMaxTokens();
        if (desiredTokens != null) {
            int safeTokens = Math.max(API_MIN_TOKENS, Math.min(API_MAX_TOKENS, desiredTokens));
            payload.put("max_tokens", safeTokens);
        }
        payload.put("stream", true);
        if (tools != null && !tools.isEmpty()) {
            payload.put("tools", tools);
            payload.put("tool_choice", "auto");
        }
        payload.put("enable_thinking", Boolean.FALSE);
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(normalizeKey(properties.getApiKey()));
            String body = mapper.writeValueAsString(payload);
            restTemplate.execute(properties.getBaseUrl(), HttpMethod.POST, request -> {
                request.getHeaders().putAll(headers);
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                request.getBody().write(bytes);
                request.getBody().flush();
            }, response -> {
                if (!response.getStatusCode().is2xxSuccessful()) {
                    if (onError != null) onError.accept(new RuntimeException("HTTP " + response.getStatusCode()));
                    return null;
                }
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                    String line;
                    StringBuilder eventBuffer = new StringBuilder();
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data:")) {
                            String payloadLine = line.substring(5);
                            if (eventBuffer.length() > 0) eventBuffer.append('\n');
                            eventBuffer.append(payloadLine);
                        } else if (line.isBlank()) {
                            if (eventBuffer.length() > 0) {
                                String raw = eventBuffer.toString().trim();
                                eventBuffer.setLength(0);
                                if ("[DONE]".equals(raw)) {
                                    if (onDone != null) onDone.run();
                                    continue;
                                }
                                parseStreamDelta(raw, onDelta);
                            }
                        }
                    }
                    if (eventBuffer.length() > 0) {
                        String raw = eventBuffer.toString().trim();
                        if (!raw.isEmpty() && !"[DONE]".equals(raw)) {
                            parseStreamDelta(raw, onDelta);
                        }
                    }
                    if (onDone != null) onDone.run();
                } catch (IOException io) {
                    if (onError != null) onError.accept(io);
                }
                return null;
            });
        } catch (Exception e) {
            log.error("Qwen stream failed: {}", e.getMessage());
            if (onError != null) onError.accept(e);
        }
    }

    private void parseStreamDelta(String raw, Consumer<ChatCompletionResponse> onDelta) {
        try {
            JsonNode node = mapper.readTree(raw);
            JsonNode deltaNode = node.at("/choices/0/delta");
            String content = deltaNode.path("content").asText(null);
            if (content != null && content.isEmpty()) content = null;
            
            List<ToolCall> toolCalls = null;
            JsonNode toolCallsNode = deltaNode.path("tool_calls");
            if (toolCallsNode.isArray() && !toolCallsNode.isEmpty()) {
                toolCalls = mapper.convertValue(toolCallsNode, new com.fasterxml.jackson.core.type.TypeReference<List<ToolCall>>(){});
            }
            
            if (content != null || toolCalls != null) {
                if (onDelta != null) onDelta.accept(new ChatCompletionResponse(content, toolCalls));
            }
        } catch (Exception parseEx) {
            // ignore
        }
    }

    private String normalizeKey(String key) {
        if (key == null) {
            return null;
        }
        return key.startsWith("sk-") ? key : "sk-" + key;
    }
}
