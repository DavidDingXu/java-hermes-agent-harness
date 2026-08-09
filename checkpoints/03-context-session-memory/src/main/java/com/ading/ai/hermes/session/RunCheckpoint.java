package com.ading.ai.hermes.session;

import com.ading.ai.hermes.core.AgentState;
import com.ading.ai.hermes.core.ToolRequest;
import java.util.List;
import java.util.Objects;

public record RunCheckpoint(
        SessionId sessionId,
        int lastEventIndex,
        AgentState state,
        ResumeDecision decision,
        List<ToolRequest> pendingToolRequests
) {

    public RunCheckpoint {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        if (lastEventIndex < -1) {
            throw new IllegalArgumentException("lastEventIndex must be -1 or greater");
        }
        Objects.requireNonNull(state, "state must not be null");
        Objects.requireNonNull(decision, "decision must not be null");
        pendingToolRequests = pendingToolRequests == null ? List.of() : List.copyOf(pendingToolRequests);
    }
}
