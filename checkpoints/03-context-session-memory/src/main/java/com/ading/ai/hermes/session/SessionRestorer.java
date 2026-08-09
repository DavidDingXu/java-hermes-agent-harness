package com.ading.ai.hermes.session;

import com.ading.ai.hermes.core.AgentEvent;
import com.ading.ai.hermes.core.AgentEventKind;
import com.ading.ai.hermes.core.AgentState;
import com.ading.ai.hermes.core.FinishReason;
import com.ading.ai.hermes.core.ToolRequest;
import java.util.LinkedHashMap;
import java.util.List;

public final class SessionRestorer {

    public RestoredSession restore(SessionRecord record) {
        AgentState state = new AgentState(record.events(), countModelTurns(record.events()));
        if (record.events().isEmpty()) {
            return RestoredSession.ready(record.sessionId(), state);
        }

        AgentEvent last = record.events().get(record.events().size() - 1);
        if (last.kind() == AgentEventKind.MODEL_FINAL_ANSWER) {
            return RestoredSession.finished(record.sessionId(), state, FinishReason.FINAL_ANSWER, last.text());
        }

        List<ToolRequest> pending = pendingToolRequests(record.events());
        if (!pending.isEmpty()) {
            return RestoredSession.pendingTools(record.sessionId(), state, pending);
        }

        return RestoredSession.ready(record.sessionId(), state);
    }

    private int countModelTurns(List<AgentEvent> events) {
        int turns = 0;
        AgentEventKind previous = null;
        for (AgentEvent event : events) {
            if (event.kind() == AgentEventKind.MODEL_FINAL_ANSWER
                    || (event.kind() == AgentEventKind.TOOL_REQUESTED
                    && previous != AgentEventKind.TOOL_REQUESTED)) {
                turns++;
            }
            previous = event.kind();
        }
        return turns;
    }

    private List<ToolRequest> pendingToolRequests(List<AgentEvent> events) {
        LinkedHashMap<String, ToolRequest> pending = new LinkedHashMap<>();
        for (AgentEvent event : events) {
            if (event.kind() == AgentEventKind.TOOL_REQUESTED) {
                pending.put(event.toolRequest().callId(), event.toolRequest());
            }
            if (event.kind() == AgentEventKind.TOOL_OBSERVED) {
                pending.remove(event.toolObservation().callId());
            }
        }
        return List.copyOf(pending.values());
    }
}
