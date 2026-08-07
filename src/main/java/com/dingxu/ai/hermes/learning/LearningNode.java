package com.dingxu.ai.hermes.learning;

import java.util.Objects;

public record LearningNode(String id, LearningNodeKind kind, String label, String content) {

    public LearningNode {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("node id must not be blank");
        }
        Objects.requireNonNull(kind, "kind must not be null");
        label = label == null ? "" : label.trim();
        content = content == null ? "" : content.trim();
    }
}
