package com.dingxu.ai.hermes.core;

import java.util.Map;

public record ToolRequest(String callId, String name, Map<String, Object> arguments) {

    public ToolRequest {
        if (callId == null || callId.isBlank()) {
            throw new IllegalArgumentException("callId must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        arguments = Map.copyOf(arguments);
    }
}
