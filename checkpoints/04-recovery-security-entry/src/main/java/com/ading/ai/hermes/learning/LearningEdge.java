package com.ading.ai.hermes.learning;

import java.util.Objects;

public record LearningEdge(String fromId, String toId, LearningEdgeKind kind) {

    public LearningEdge {
        if (fromId == null || fromId.isBlank() || toId == null || toId.isBlank()) {
            throw new IllegalArgumentException("edge node ids must not be blank");
        }
        Objects.requireNonNull(kind, "kind must not be null");
    }
}
