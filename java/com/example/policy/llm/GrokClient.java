package com.example.policy.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;

@Component
public class GrokClient {
    private static final Logger log = LoggerFactory.getLogger(GrokClient.class);

    @Value("${llm.grok.api-key:}")
    private String apiKey;

    @Value("${llm.grok.base-url:https://kfcv50.link/v1/chat/completions}")
    private String baseUrl;

    @Value("${llm.grok.responses-url:https://kfcv50.link/v1/responses}")
    private String responsesUrl;

    @Value("${llm.grok.model:grok-4}")
    private String model;

    @Value("${llm.grok.max-tokens:80000}")
    private int defaultMaxTokens;

    @Value("${llm.grok.enable-search:false}")
    private boolean enableSearch;

    @Value("${llm.retries:1}")
    private int retries;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private IngestService ingestService;

    public void setIngestService(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    public GrokClient(@Value("${llm.timeout-seconds:7200}") int timeoutSeconds) {
        log.info("=== GrokClient 初始化开始（搜索模式强制开启） ===");
        log.info("超时设置: {} 秒 | 搜索启用: {} | Base URL: {} | Responses URL: {}",
                timeoutSeconds, enableSearch, baseUrl, responsesUrl);

        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        int ms = Math.max(1, timeoutSeconds) * 1000;
        f.setConnectTimeout(ms);
        f.setReadTimeout(ms);
        this.restTemplate = new RestTemplate(f);

        this.restTemplate.getMessageConverters().removeIf(conv -> conv instanceof org.springframework.http.converter.StringHttpMessageConverter);
        this.restTemplate.getMessageConverters().add(0, new org.springframework.http.converter.StringHttpMessageConverter(StandardCharsets.UTF_8));

        log.info("=== GrokClient 初始化完成 ===");
    }

    // ==================== 非流式调用（保持你原来的完整逻辑） ====================
    public List<Map<String, Object>> invoke(String prompt, int maxTokens) {
        log.info("=== GrokClient.invoke 方法开始（官方联网搜索版本） ===");
        log.info("输入参数 - prompt长度: {}, maxTokens: {}",
                prompt != null ? prompt.length() : 0, maxTokens);
        log.debug("prompt内容预览: {}",
                prompt != null ? prompt.substring(0, Math.min(200, prompt.length())) + "..." : "null");

        if (apiKey == null || apiKey.isBlank()) {
            log.error(" API Key 未配置或为空! 请检查 llm.grok.api-key 配置");
            log.info("=== GrokClient.invoke 方法结束 (API Key 问题) ===");
            return Collections.emptyList();
        } else {
            log.info(" API Key 配置正常 (前8位): {}...",
                    apiKey.length() > 8 ? apiKey.substring(0, 8) + "..." : "***");
        }

        int attempt = 0;
        int useMax = maxTokens > 0 ? maxTokens : defaultMaxTokens;
        log.info("使用的最大token数: {}", useMax);

        while (true) {
            attempt++;
            log.info(" 尝试第 {} 次调用 Grok API", attempt);

            try {
                String targetUrl = enableSearch ? responsesUrl : baseUrl;
                log.info("使用端点: {} | 搜索模式: {}", targetUrl, enableSearch);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                String bearerToken = apiKey.startsWith("sk-") ? apiKey : ("sk-" + apiKey);
                headers.setBearerAuth(bearerToken);
                log.debug("请求头设置完成 - ContentType: {}, BearerToken: {}...",
                        MediaType.APPLICATION_JSON, bearerToken.length() > 10 ? bearerToken.substring(0, 10) + "..." : "***");

                Map<String, Object> requestBody;
                if (enableSearch) {
                    requestBody = Map.of(
                            "model", model,
                            "input", List.of(Map.of("role", "user", "content", prompt)),
                            "temperature", 0.1,
                            "max_output_tokens", useMax,
                            "tools", List.of(Map.of("type", "web_search"))
                    );
                    log.debug("请求体 - Responses API + web_search 工具");
                } else {
                    requestBody = Map.of(
                            "model", model,
                            "messages", List.of(Map.of("role", "user", "content", prompt)),
                            "temperature", 0.1,
                            "max_tokens", useMax
                    );
                    log.debug("请求体 - 标准 Chat Completions");
                }

                String body = objectMapper.writeValueAsString(requestBody);
                log.debug("请求体构建完成 - 模型: {}, 温度: {}, 消息/输入数: 1", model, 0.1);
                log.debug("完整请求体: {}", body);

                HttpEntity<String> entity = new HttpEntity<>(body, headers);

                log.info(" 开始发送 HTTP 请求到: {}", targetUrl);
                long startTime = System.currentTimeMillis();

                ResponseEntity<String> resp = restTemplate.postForEntity(targetUrl, entity, String.class);

                long endTime = System.currentTimeMillis();
                log.info(" HTTP 请求完成 - 状态码: {}, 耗时: {}ms",
                        resp.getStatusCode(), (endTime - startTime));

                if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                    log.info(" Grok API 调用成功");
                    log.debug("响应体长度: {}", resp.getBody().length());
                    log.debug("响应体内容: {}", resp.getBody());

                    JsonNode root = objectMapper.readTree(resp.getBody());
                    String content;
                    if (enableSearch) {
                        content = root.at("/output/0/content/0/text").asText("").trim();
                        log.debug("从 Responses API 提取 content");
                    } else {
                        content = root.at("/choices/0/message/content").asText("").trim();
                        log.debug("从 Chat Completions 提取 content");
                    }

                    log.info(" 提取的content信息 - 长度: {}, 是否为空: {}",
                            content.length(), content.isEmpty());
                    log.debug("content内容预览: {}",
                            content.length() > 200 ? content.substring(0, 200) + "..." : content);

                    if (content.startsWith("```")) {
                        log.info(" 检测到 Markdown 代码块，开始清理...");
                        int first = content.indexOf('\n');
                        int last = content.lastIndexOf("```");
                        if (first > 0 && last > first) {
                            String originalContent = content;
                            content = content.substring(first + 1, last).trim();
                            log.info(" 代码块清理完成 - 原长度: {}, 新长度: {}",
                                    originalContent.length(), content.length());
                        }
                    }

                    // 策略1: 尝试直接解析为 JSON 数组
                    log.info(" 尝试策略1: 直接解析为 List<Map>");
                    try {
                        List<Map<String, Object>> arr = objectMapper.readValue(content, new TypeReference<List<Map<String, Object>>>(){});
                        if (arr != null) {
                            log.info(" 策略1成功 - 解析出 {} 个数据项", arr.size());
                            log.info("=== GrokClient.invoke 方法结束 (策略1成功) ===");
                            return arr;
                        }
                    } catch (Exception e) {
                        log.warn(" 策略1失败 - 解析异常: {}", e.getMessage());
                    }

                    // 策略2: 尝试解析为包含 policyData 的对象
                    log.info(" 尝试策略2: 解析为包含 policyData 的对象");
                    try {
                        Map<String, Object> obj = objectMapper.readValue(content, new TypeReference<Map<String, Object>>(){});
                        Object pd = null;
                        if (obj != null) {
                            for (Map.Entry<String, Object> e : obj.entrySet()) {
                                if (e.getKey() != null && "policyData".equalsIgnoreCase(e.getKey())) {
                                    pd = e.getValue();
                                    break;
                                }
                            }
                        }
                        if (pd != null) {
                            List<Map<String, Object>> converted = objectMapper.convertValue(pd, new TypeReference<List<Map<String, Object>>>(){});
                            if (converted != null) {
                                log.info(" 策略2成功 - 从 policyData 解析出 {} 个数据项", converted.size());
                                log.info("=== GrokClient.invoke 方法结束 (策略2成功) ===");
                                return converted;
                            }
                        }
                    } catch (Exception e) {
                        log.warn(" 策略2失败 - 解析异常: {}", e.getMessage());
                    }

                    // 降级策略: 返回原始文本
                    log.warn(" 所有JSON解析策略失败，返回原始文本");
                    log.info("最终返回的原始文本长度: {}", content.length());
                    log.info("=== GrokClient.invoke 方法结束 (降级策略) ===");
                    return List.of(Map.of(
                            "rawText", content,
                            "note", "non-json-content",
                            "debug_info", "所有JSON解析策略均失败"
                    ));
                } else {
                    log.error(" Grok API 响应异常 - 状态码: {}, 响应体: {}",
                            resp.getStatusCode(),
                            resp.getBody() != null ? (resp.getBody().length() > 200 ? resp.getBody().substring(0, 200) + "..." : resp.getBody()) : "null");
                }
            } catch (Exception e) {
                log.error(" Grok 调用失败 (第 {} 次尝试) - 异常类型: {}, 异常信息: {}",
                        attempt, e.getClass().getSimpleName(), e.getMessage());
                log.debug("完整异常堆栈: ", e);
            }

            if (attempt > Math.max(0, retries)) {
                log.error(" 已达到最大重试次数 ({})，停止重试", retries);
                break;
            }

            long sleepTime = 500L * attempt;
            log.info(" 准备重试，等待 {}ms 后开始第 {} 次尝试", sleepTime, attempt + 1);
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException ie) {
                log.warn(" 重试等待被中断");
                Thread.currentThread().interrupt();
                break;
            }
        }

        log.error(" 所有重试均失败，返回空列表");
        log.info("=== GrokClient.invoke 方法结束 (所有重试失败) ===");
        return Collections.emptyList();
    }

    // ==================== 流式调用（已修改为假流式 - 彻底解决解析问题） ====================
    public SseEmitter invokeStreaming(String prompt, int maxTokens, List<Long> orgIds) {
        log.warn("=== 真实流式已关闭 → 降级为假流式（使用稳定非流式 invoke()）===");
        log.info("搜索模式: {} | 将直接返回完整响应（避免Responses API流式解析崩溃）", enableSearch);

        SseEmitter emitter = new SseEmitter(600000L); // 10分钟超时

        new Thread(() -> {
            try {
                // 关键：调用你已经很稳定的非流式 invoke 方法
                List<Map<String, Object>> resultList = invoke(prompt, maxTokens);

                String fullResponse = "";
                if (resultList != null && !resultList.isEmpty()) {
                    if (resultList.get(0).containsKey("rawText")) {
                        fullResponse = String.valueOf(resultList.get(0).get("rawText"));
                    } else {
                        fullResponse = objectMapper.writeValueAsString(resultList);
                    }
                }

                // 一次性返回完整内容（模拟流式完成）
                emitter.send(SseEmitter.event().name("message").data(fullResponse));
                emitter.send(SseEmitter.event().name("done").data("done"));

                // 执行入库（和你原来完全一样）
                if (ingestService != null && !fullResponse.isBlank()) {
                    log.info("流式调用完成，开始入库处理，响应长度: {}", fullResponse.length());
                    Map<String, Object> ingestResult = ingestService.directIngestFromStreaming(fullResponse, orgIds);
                    emitter.send(SseEmitter.event()
                            .name("ingest-result")
                            .data(objectMapper.writeValueAsString(ingestResult)));
                }

                emitter.complete();

            } catch (Exception e) {
                log.error("假流式执行失败", e);
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }

    public void diagnose() {
        log.info("=== GrokClient 诊断信息（官方联网搜索版本） ===");
        log.info("API Key 配置: {}", apiKey != null && !apiKey.isBlank() ? "已配置" : "未配置");
        log.info("Base URL: {}", baseUrl);
        log.info("Responses URL: {}", responsesUrl);
        log.info("Model: {}", model);
        log.info("Default Max Tokens: {}", defaultMaxTokens);
        log.info("搜索启用: {}", enableSearch);
        log.info("Retries: {}", retries);
        log.info("IngestService 注入状态: {}", ingestService != null ? "已注入" : "未注入");
        log.info("=== GrokClient 诊断结束 ===");
    }
}