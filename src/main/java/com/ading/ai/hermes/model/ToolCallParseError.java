package com.ading.ai.hermes.model;

import java.util.Objects;

public record ToolCallParseError(ToolCallParseErrorKind kind, String callId, String toolName, String message) {

    public ToolCallParseError {
        Objects.requireNonNull(kind, "kind must not be null");
        callId = callId == null ? "" : callId;
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName must not be blank");
        }
        message = message == null ? "" : message;
    }
}
