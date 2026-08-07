package com.dingxu.ai.hermes.memory;

import java.util.Objects;

public record MemoryDecision(
        MemoryDecisionKind kind,
        MemoryTarget target,
        String reason,
        String normalizedContent
) {

    public MemoryDecision {
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(normalizedContent, "normalizedContent must not be null");
    }

    public static MemoryDecision accept(MemoryTarget target, String reason, String normalizedContent) {
        Objects.requireNonNull(target, "target must not be null");
        return new MemoryDecision(MemoryDecisionKind.ACCEPT, target, reason, normalizedContent);
    }

    public static MemoryDecision reject(String reason) {
        return new MemoryDecision(MemoryDecisionKind.REJECT, null, reason, "");
    }
}
