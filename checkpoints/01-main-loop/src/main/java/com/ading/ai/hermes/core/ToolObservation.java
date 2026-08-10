package com.ading.ai.hermes.core;

import java.util.Objects;

public record ToolObservation(
        String callId,
        boolean success,
        String content,
        ToolFailureKind failureKind
) {

    public ToolObservation {
        if (callId == null || callId.isBlank()) {
            throw new IllegalArgumentException("callId must not be blank");
        }
        content = content == null ? "" : content;
        Objects.requireNonNull(failureKind, "failureKind must not be null");
        if (success && failureKind != ToolFailureKind.NONE) {
            throw new IllegalArgumentException("successful observation must not have a failure kind");
        }
        if (!success && failureKind == ToolFailureKind.NONE) {
            throw new IllegalArgumentException("failed observation must have a failure kind");
        }
    }

    public ToolObservation(String callId, boolean success, String content) {
        this(callId, success, content, success ? ToolFailureKind.NONE : ToolFailureKind.REJECTED);
    }

    public static ToolObservation success(String callId, String content) {
        return new ToolObservation(callId, true, content, ToolFailureKind.NONE);
    }

    public static ToolObservation failure(String callId, String content) {
        return new ToolObservation(
                callId,
                false,
                Objects.requireNonNullElse(content, ""),
                ToolFailureKind.REJECTED
        );
    }

    public static ToolObservation executionFailure(String callId, String content) {
        return new ToolObservation(
                callId,
                false,
                Objects.requireNonNullElse(content, ""),
                ToolFailureKind.EXECUTION_ERROR
        );
    }
}
