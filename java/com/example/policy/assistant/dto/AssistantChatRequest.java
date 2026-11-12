package com.example.policy.assistant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

/**
 * REST request payload for invoking the policy analysis assistant.
 */
public class AssistantChatRequest {

    @Valid
    @NotEmpty
    @Size(max = 10)
    private List<AssistantMessage> messages = new ArrayList<>();

    private boolean stream;

    public AssistantChatRequest() {
    }

    public List<AssistantMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<AssistantMessage> messages) {
        this.messages = messages;
    }

    public boolean isStream() {
        return stream;
    }

    public void setStream(boolean stream) {
        this.stream = stream;
    }
}