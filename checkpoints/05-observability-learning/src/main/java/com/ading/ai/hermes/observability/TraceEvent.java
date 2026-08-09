package com.ading.ai.hermes.observability;

import java.util.Map;
import java.util.Objects;

public record TraceEvent(
        TraceEventKind kind,
        String sessionId,
        String turnId,
        String taskId,
        String parentTurnId,
        String occurredAt,
        Map<String, String> attributes
) {

    public TraceEvent {
        Objects.requireNonNull(kind, "kind must not be null");
        sessionId = sessionId == null ? "" : sessionId;
        turnId = turnId == null ? "" : turnId;
        taskId = taskId == null ? "" : taskId;
        parentTurnId = parentTurnId == null ? "" : parentTurnId;
        occurredAt = occurredAt == null ? "" : occurredAt;
        attributes = Map.copyOf(Objects.requireNonNullElse(attributes, Map.of()));
    }
}
