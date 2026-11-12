package com.example.policy.assistant.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Represents a single conversation turn message exchanged between the user and the policy assistant.
 */
public class AssistantMessage {

    @NotBlank
    private String role;

    @NotBlank
    private String content;

    public AssistantMessage() {
    }

    public AssistantMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}