package com.example.policy.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "assistant")
public class AssistantProperties {

    /**
     * Assistant display name exposed to end users.
     */
    private String name = "政策分析助手";

    private final Sql sql = new Sql();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Sql getSql() {
        return sql;
    }

    public static class Sql {
        /**
         * Maximum rows returned from a single SQL query.
         */
        private int maxLimit = 50;
        /**
         * Maximum time (seconds) allowed for a SQL execution before timing out.
         */
        private int timeoutSeconds = 12;
        /**
         * Whether write operations are allowed. Should remain false for safety.
         */
        private boolean allowWrite = false;

        public int getMaxLimit() {
            return maxLimit;
        }

        public void setMaxLimit(int maxLimit) {
            this.maxLimit = maxLimit;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public boolean isAllowWrite() {
            return allowWrite;
        }

        public void setAllowWrite(boolean allowWrite) {
            this.allowWrite = allowWrite;
        }
    }
}
