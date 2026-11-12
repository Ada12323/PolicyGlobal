package com.example.policy.assistant.tool;

import java.util.Map;

public class ToolDefinition {
    private String type = "function";
    private FunctionDef function;

    public ToolDefinition(String name, String description, Map<String, Object> parameters) {
        this.function = new FunctionDef(name, description, parameters);
    }

    public String getType() { return type; }
    public FunctionDef getFunction() { return function; }

    public static class FunctionDef {
        private String name;
        private String description;
        private Map<String, Object> parameters;

        public FunctionDef(String name, String description, Map<String, Object> parameters) {
            this.name = name;
            this.description = description;
            this.parameters = parameters;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public Map<String, Object> getParameters() { return parameters; }
    }
}
