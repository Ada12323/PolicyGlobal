package com.example.policy.assistant.dto;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Standardized assistant response payload.
 */
public class AssistantChatResponse {

    private String reply;
    private List<Map<String, Object>> sources;
    private List<String> thoughts;
    private String error;

    public static AssistantChatResponse success(String reply, List<Map<String, Object>> sources, List<String> thoughts) {
        AssistantChatResponse r = new AssistantChatResponse();
        r.setReply(reply);
        r.setSources(sources != null ? sources : Collections.emptyList());
        r.setThoughts(thoughts != null ? thoughts : Collections.emptyList());
        return r;
    }

    public static AssistantChatResponse failure(String message) {
        AssistantChatResponse r = new AssistantChatResponse();
        r.setError(message);
        return r;
    }

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public List<Map<String, Object>> getSources() {
        return sources;
    }

    public void setSources(List<Map<String, Object>> sources) {
        this.sources = sources;
    }

    public List<String> getThoughts() {
        return thoughts;
    }

    public void setThoughts(List<String> thoughts) {
        this.thoughts = thoughts;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}