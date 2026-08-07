package com.dingxu.ai.hermes.runtime;

import java.util.Objects;

public record RuntimeBoundary(
        BoundaryKind kind,
        String name,
        String input,
        String output,
        String responsibility,
        String hermesEvidence
) {

    public RuntimeBoundary {
        Objects.requireNonNull(kind, "kind must not be null");
        requireText(name, "name");
        requireText(input, "input");
        requireText(output, "output");
        requireText(responsibility, "responsibility");
        requireText(hermesEvidence, "hermesEvidence");
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
