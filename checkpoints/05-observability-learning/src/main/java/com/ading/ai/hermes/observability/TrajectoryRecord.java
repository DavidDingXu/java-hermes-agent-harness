package com.ading.ai.hermes.observability;

import java.util.List;

public record TrajectoryRecord(
        String sessionId,
        String turnId,
        String createdAt,
        List<TraceEvent> events
) {

    public TrajectoryRecord {
        sessionId = sessionId == null ? "" : sessionId;
        turnId = turnId == null ? "" : turnId;
        createdAt = createdAt == null ? "" : createdAt;
        events = List.copyOf(events);
    }
}
