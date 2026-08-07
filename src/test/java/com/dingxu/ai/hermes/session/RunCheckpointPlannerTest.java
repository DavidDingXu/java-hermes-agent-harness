package com.dingxu.ai.hermes.session;

import com.dingxu.ai.hermes.core.AgentEvent;
import com.dingxu.ai.hermes.core.ToolObservation;
import com.dingxu.ai.hermes.core.ToolRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RunCheckpointPlannerTest {

    @Test
    void resumesAfterInterruptedSafeBoundaryByCallingModelAgain() {
        RunCheckpointPlanner planner = new RunCheckpointPlanner(new SessionRestorer());
        SessionRecord record = new SessionRecord(new SessionId("session-1"), List.of(
                AgentEvent.userMessage("read README"),
                AgentEvent.toolRequested(new ToolRequest("call-1", "read_file", Map.of("path", "README.md"))),
                AgentEvent.toolObserved(ToolObservation.success("call-1", "README content")),
                AgentEvent.runInterrupted("user sent /stop")
        ));

        RunCheckpoint checkpoint = planner.plan(record);

        assertEquals(new SessionId("session-1"), checkpoint.sessionId());
        assertEquals(3, checkpoint.lastEventIndex());
        assertEquals(ResumeDecision.READY_FOR_MODEL, checkpoint.decision());
        assertEquals(1, checkpoint.state().turnsUsed());
    }

    @Test
    void keepsPendingToolRequestWhenInterruptionHappensBeforeObservation() {
        RunCheckpointPlanner planner = new RunCheckpointPlanner(new SessionRestorer());
        ToolRequest pending = new ToolRequest("call-1", "write_file", Map.of("path", "notes.md"));
        SessionRecord record = new SessionRecord(new SessionId("session-1"), List.of(
                AgentEvent.userMessage("write notes"),
                AgentEvent.toolRequested(pending),
                AgentEvent.runInterrupted("user sent /stop before observation")
        ));

        RunCheckpoint checkpoint = planner.plan(record);

        assertEquals(2, checkpoint.lastEventIndex());
        assertEquals(ResumeDecision.PENDING_TOOL_OBSERVATION, checkpoint.decision());
        assertEquals(List.of(pending), checkpoint.pendingToolRequests());
    }
}
