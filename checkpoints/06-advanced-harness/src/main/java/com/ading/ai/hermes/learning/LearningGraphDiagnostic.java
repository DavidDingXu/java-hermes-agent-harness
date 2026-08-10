package com.ading.ai.hermes.learning;

import java.util.Objects;

public record LearningGraphDiagnostic(
        LearningDiagnosticCode code,
        String sourceId,
        String targetId,
        String message
) {

    public LearningGraphDiagnostic {
        Objects.requireNonNull(code, "code must not be null");
        sourceId = requireText(sourceId, "sourceId");
        targetId = requireText(targetId, "targetId");
        message = requireText(message, "message");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
