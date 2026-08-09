package com.ading.ai.hermes.gateway;

import com.ading.ai.hermes.core.AgentEvent;
import com.ading.ai.hermes.core.FinishReason;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record GatewayTurnResponse(
        String conversationId,
        String sessionKey,
        String finalAnswer,
        FinishReason finishReason,
        List<AgentEvent> events,
        Map<String, String> metadata
) {

    public GatewayTurnResponse {
        conversationId = conversationId == null ? "" : conversationId;
        sessionKey = sessionKey == null ? "" : sessionKey;
        finalAnswer = finalAnswer == null ? "" : finalAnswer;
        Objects.requireNonNull(finishReason, "finishReason must not be null");
        events = events == null ? List.of() : List.copyOf(events);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
