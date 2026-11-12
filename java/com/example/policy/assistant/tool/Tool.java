package com.example.policy.assistant.tool;

public interface Tool {
    String getName();
    ToolDefinition getDefinition();
    String execute(String argumentsJson);
}
