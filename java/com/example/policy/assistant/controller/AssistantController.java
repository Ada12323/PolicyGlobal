package com.example.policy.assistant.controller;

import com.example.policy.assistant.dto.AssistantChatRequest;
import com.example.policy.assistant.dto.AssistantChatResponse;
import com.example.policy.assistant.service.PolicyAssistantService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final PolicyAssistantService policyAssistantService;

    public AssistantController(PolicyAssistantService policyAssistantService) {
        this.policyAssistantService = policyAssistantService;
    }

    @PostMapping(value = "/chat", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
    public Object chat(@Valid @RequestBody AssistantChatRequest request) {
        if (request.isStream()) {
            SseEmitter emitter = policyAssistantService.chatStream(request);
            return emitter;
        }
        AssistantChatResponse response = policyAssistantService.chat(request);
        return response;
    }
}
