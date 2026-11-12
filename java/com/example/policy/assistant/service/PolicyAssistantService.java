package com.example.policy.assistant.service;

import com.example.policy.assistant.client.QwenClient;
import com.example.policy.assistant.dto.AssistantChatRequest;
import com.example.policy.assistant.dto.AssistantChatResponse;
import com.example.policy.assistant.dto.AssistantMessage;
import com.example.policy.assistant.prompt.PolicyAssistantPrompts;
import com.example.policy.assistant.dto.ChatCompletionResponse;
import com.example.policy.assistant.dto.ToolCall;
import com.example.policy.assistant.tool.Tool;
import com.example.policy.assistant.tool.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import jakarta.annotation.PreDestroy;

import org.springframework.security.concurrent.DelegatingSecurityContextExecutorService;

@Service
public class PolicyAssistantService {

    private static final Logger log = LoggerFactory.getLogger(PolicyAssistantService.class);

    private final QwenClient qwenClient;
    private final PolicyAssistantPrompts prompts;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorService streamExecutor;

    public PolicyAssistantService(QwenClient qwenClient,
                                  PolicyAssistantPrompts prompts,
                                  ToolRegistry toolRegistry) {
        this.qwenClient = qwenClient;
        this.prompts = prompts;
        this.toolRegistry = toolRegistry;
        ExecutorService delegate = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setName("assistant-stream-" + t.getId());
            t.setDaemon(true);
            return t;
        });
        this.streamExecutor = new DelegatingSecurityContextExecutorService(delegate);
    }

    public AssistantChatResponse chat(AssistantChatRequest request) {
        return executeChat(request);
    }

    public SseEmitter chatStream(AssistantChatRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        emitter.onTimeout(() -> {
            log.warn("Assistant stream timed out");
            emitter.complete();
        });
        emitter.onError(t -> log.warn("Assistant stream transport error: {}", t.getMessage()));

        streamExecutor.submit(() -> {
            try {
                List<AssistantMessage> history = sanitizeHistory(request.getMessages());
                List<Map<String, Object>> baseMessages = buildBaseMessages(history);
                List<String> thoughts = new ArrayList<>();
                
                for (int i = 0; i < 5; i++) {
                    ChatCompletionResponse response = qwenClient.completeWithTools(baseMessages, toolRegistry.getAllDefinitions(), null);
                    
                    if (response.getToolCalls() != null && !response.getToolCalls().isEmpty()) {
                        Map<String, Object> assistantMsg = new HashMap<>();
                        assistantMsg.put("role", "assistant");
                        assistantMsg.put("content", response.getContent() == null ? "" : response.getContent());
                        assistantMsg.put("tool_calls", response.getToolCalls());
                        baseMessages.add(assistantMsg);
                        
                        for (ToolCall call : response.getToolCalls()) {
                            String toolName = call.getFunction().getName();
                            String arguments = call.getFunction().getArguments();
                            thoughts.add("[MODEL-TOOL-CALL] " + toolName + " " + arguments);
                            Tool tool = toolRegistry.getTool(toolName);
                            String toolResult;
                            if (tool != null) {
                                toolResult = tool.execute(arguments);
                            } else {
                                toolResult = "Tool not found: " + toolName;
                            }
                            
                            Map<String, Object> toolMsg = new HashMap<>();
                            toolMsg.put("role", "tool");
                            toolMsg.put("tool_call_id", call.getId());
                            toolMsg.put("name", toolName);
                            toolMsg.put("content", toolResult);
                            baseMessages.add(toolMsg);
                        }
                    } else {
                        sendMeta(emitter, AssistantChatResponse.success(null, Collections.emptyList(), thoughts));
                        qwenClient.streamWithTools(baseMessages, null, null,
                                delta -> {
                                    if (delta.getContent() != null) {
                                        safeEmit(emitter, SseEmitter.event().name("delta").data(delta.getContent()));
                                    }
                                },
                                () -> {
                                    safeEmit(emitter, SseEmitter.event().name("done").data(Map.of("reply", "")));
                                    try { emitter.complete(); } catch (Exception ignore) {}
                                },
                                err -> safeEmit(emitter, SseEmitter.event().name("error").data(err.getMessage()))
                        );
                        return;
                    }
                }
                
                emitter.send(SseEmitter.event().name("error").data("工具调用次数超过最大限制"));
                emitter.complete();
            } catch (Exception ex) {
                log.warn("Assistant stream failed", ex);
                try {
                    emitter.send(SseEmitter.event().name("error").data(ex.getMessage()));
                } catch (Exception io) {
                    log.debug("Failed to push error event: {}", io.getMessage());
                }
                emitter.complete();
            }
        });

        return emitter;
    }

    private void safeEmit(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException ignored) {
        }
    }

    @PreDestroy
    public void shutdown() {
        streamExecutor.shutdownNow();
    }

    private AssistantChatResponse executeChat(AssistantChatRequest request) {
        List<AssistantMessage> history = sanitizeHistory(request.getMessages());
        List<Map<String, Object>> baseMessages = buildBaseMessages(history);
        List<String> thoughts = new ArrayList<>();
        
        try {
            for (int i = 0; i < 5; i++) {
                ChatCompletionResponse response = qwenClient.completeWithTools(baseMessages, toolRegistry.getAllDefinitions(), null);
                
                if (response.getToolCalls() != null && !response.getToolCalls().isEmpty()) {
                    Map<String, Object> assistantMsg = new HashMap<>();
                    assistantMsg.put("role", "assistant");
                    assistantMsg.put("content", response.getContent() == null ? "" : response.getContent());
                    assistantMsg.put("tool_calls", response.getToolCalls());
                    baseMessages.add(assistantMsg);
                    
                    for (ToolCall call : response.getToolCalls()) {
                        String toolName = call.getFunction().getName();
                        String arguments = call.getFunction().getArguments();
                        thoughts.add("[MODEL-TOOL-CALL] " + toolName + " " + arguments);
                        Tool tool = toolRegistry.getTool(toolName);
                        String toolResult;
                        if (tool != null) {
                            toolResult = tool.execute(arguments);
                        } else {
                            toolResult = "Tool not found: " + toolName;
                        }
                        
                        Map<String, Object> toolMsg = new HashMap<>();
                        toolMsg.put("role", "tool");
                        toolMsg.put("tool_call_id", call.getId());
                        toolMsg.put("name", toolName);
                        toolMsg.put("content", toolResult);
                        baseMessages.add(toolMsg);
                    }
                } else {
                    thoughts.add("[MODEL-FINAL] " + response.getContent());
                    return AssistantChatResponse.success(response.getContent(), Collections.emptyList(), thoughts);
                }
            }
            return AssistantChatResponse.failure("工具调用次数超过最大限制");
        } catch (Exception e) {
            log.warn("Assistant chat failed: {}", e.getMessage());
            return AssistantChatResponse.failure(e.getMessage());
        }
    }

    private void sendMeta(SseEmitter emitter, AssistantChatResponse result) throws IOException {
        Map<String, Object> meta = Map.of(
                "sources", result.getSources() != null ? result.getSources() : Collections.emptyList(),
                "thoughts", result.getThoughts() != null ? result.getThoughts() : Collections.emptyList()
        );
        emitter.send(SseEmitter.event().name("meta").data(meta));
    }

    private List<Map<String, Object>> buildBaseMessages(List<AssistantMessage> history) {
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> sysMsg = new HashMap<>();
        sysMsg.put("role", "system");
        sysMsg.put("content", prompts.systemPrompt());
        messages.add(sysMsg);
        
        for (AssistantMessage msg : history) {
            Map<String, Object> map = new HashMap<>();
            map.put("role", normalizeRole(msg.getRole()));
            map.put("content", msg.getContent());
            messages.add(map);
        }
        return messages;
    }

    private List<AssistantMessage> sanitizeHistory(List<AssistantMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        int max = 10;
        List<AssistantMessage> slice = messages.size() > max ? messages.subList(messages.size() - max, messages.size()) : messages;
        return slice.stream()
                .filter(msg -> msg.getContent() != null && !msg.getContent().isBlank())
                .collect(Collectors.toList());
    }

    private String normalizeRole(String role) {
        if (role == null) {
            return "user";
        }
        return switch (role) {
            case "assistant", "system" -> role;
            default -> "user";
        };
    }
}
