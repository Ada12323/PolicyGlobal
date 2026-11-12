package com.example.policy.assistant.dto;

import java.util.List;

public class ChatCompletionResponse {
    private String content;
    private List<ToolCall> toolCalls;

    public ChatCompletionResponse() {}

    public ChatCompletionResponse(String content, List<ToolCall> toolCalls) {
        this.content = content;
        this.toolCalls = toolCalls;
    }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public List<ToolCall> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<ToolCall> toolCalls) { this.toolCalls = toolCalls; }
}
