package com.ading.ai.hermes.core;

import java.util.Objects;

public record ToolObservation(String callId, boolean success, String content) {

    public ToolObservation {
        if (callId == null || callId.isBlank()) {
            throw new IllegalArgumentException("callId must not be blank");
        }
        content = content == null ? "" : content;
    }

    public static ToolObservation success(String callId, String content) {
        return new ToolObservation(callId, true, content);
    }

    public static ToolObservation failure(String callId, String content) {
        return new ToolObservation(callId, false, Objects.requireNonNullElse(content, ""));
    }
}
