package com.example.policy.assistant.tool;

import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ToolRegistry {
    private final Map<String, Tool> toolMap;

    public ToolRegistry(List<Tool> tools) {
        this.toolMap = tools.stream().collect(Collectors.toMap(Tool::getName, Function.identity()));
    }

    public Tool getTool(String name) {
        return toolMap.get(name);
    }

    public List<ToolDefinition> getAllDefinitions() {
        return toolMap.values().stream().map(Tool::getDefinition).collect(Collectors.toList());
    }
}
