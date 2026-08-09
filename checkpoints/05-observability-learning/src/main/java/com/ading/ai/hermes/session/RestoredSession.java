package com.ading.ai.hermes.session;

import com.ading.ai.hermes.core.AgentState;
import com.ading.ai.hermes.core.FinishReason;
import com.ading.ai.hermes.core.ToolRequest;
import java.util.List;

public record RestoredSession(
        SessionId sessionId,
        AgentState state,
        ResumeDecision decision,
        List<ToolRequest> pendingToolRequests,
        FinishReason finishReason,
        String finalAnswer
) {

    public RestoredSession {
        finalAnswer = finalAnswer == null ? "" : finalAnswer;
        pendingToolRequests = pendingToolRequests == null ? List.of() : List.copyOf(pendingToolRequests);
    }

    public static RestoredSession ready(SessionId sessionId, AgentState state) {
        return new RestoredSession(sessionId, state, ResumeDecision.READY_FOR_MODEL, List.of(), null, "");
    }

    public static RestoredSession pendingTools(
            SessionId sessionId,
            AgentState state,
            List<ToolRequest> requests
    ) {
        return new RestoredSession(
                sessionId, state, ResumeDecision.PENDING_TOOL_OBSERVATION, requests, null, ""
        );
    }

    public static RestoredSession finished(
            SessionId sessionId,
            AgentState state,
            FinishReason finishReason,
            String finalAnswer
    ) {
        return new RestoredSession(
                sessionId, state, ResumeDecision.FINISHED, List.of(), finishReason, finalAnswer
        );
    }
}
