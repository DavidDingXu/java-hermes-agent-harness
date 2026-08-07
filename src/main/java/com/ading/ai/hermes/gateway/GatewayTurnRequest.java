package com.ading.ai.hermes.gateway;

import java.util.Map;

public record GatewayTurnRequest(
        String source,
        String conversationId,
        String userMessage,
        Map<String, String> metadata
) {

    public GatewayTurnRequest {
        source = source == null ? "" : source.trim();
        conversationId = conversationId == null ? "" : conversationId.trim();
        userMessage = userMessage == null ? "" : userMessage;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
