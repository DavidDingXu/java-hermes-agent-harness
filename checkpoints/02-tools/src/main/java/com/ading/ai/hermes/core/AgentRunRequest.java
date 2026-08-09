package com.ading.ai.hermes.core;

import java.util.Map;
import java.util.Objects;

public record AgentRunRequest(
        String source,
        String conversationId,
        String userMessage,
        IterationBudget budget,
        Map<String, String> metadata
) {

    public AgentRunRequest {
        source = source == null ? "" : source.trim();
        conversationId = conversationId == null ? "" : conversationId.trim();
        if (source.isBlank()) {
            throw new IllegalArgumentException("source must not be blank");
        }
        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException("userMessage must not be blank");
        }
        Objects.requireNonNull(budget, "budget must not be null");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public AgentRunRequest(String userMessage, IterationBudget budget) {
        this("direct", "", userMessage, budget, Map.of());
    }

    public static AgentRunRequest start(String userMessage, IterationBudget budget) {
        return new AgentRunRequest(userMessage, budget);
    }

    public static AgentRunRequest from(
            String source,
            String conversationId,
            String userMessage,
            IterationBudget budget,
            Map<String, String> metadata
    ) {
        return new AgentRunRequest(source, conversationId, userMessage, budget, metadata);
    }
}
