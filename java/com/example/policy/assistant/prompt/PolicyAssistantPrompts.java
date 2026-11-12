package com.example.policy.assistant.prompt;

import com.example.policy.assistant.config.AssistantProperties;
import org.springframework.stereotype.Component;

@Component
public class PolicyAssistantPrompts {

    private final AssistantProperties assistantProperties;

    public PolicyAssistantPrompts(AssistantProperties assistantProperties) {
        this.assistantProperties = assistantProperties;
    }

    public String systemPrompt() {
        return "你是一名政策分析助手，名为 " + assistantProperties.getName() + "。" +
            "\n核心原则：" +
            "\n1. 你拥有多个工具，当需要查询事实时优先调用工具。" +
            "\n2. 允许将用户自然语言映射为工具参数。如果数据库不存在完全匹配字段，应优先映射到 keyword 或 orgName 等可查询参数。" +
            "\n3. 不得编造政策数据。如果查询不到数据，如实告知用户数据库中不存在对应信息。" +
            "\n4. 最终回答必须基于工具返回结果，条理清晰，可读性好。";
    }

}
