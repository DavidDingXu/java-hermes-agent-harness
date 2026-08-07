package com.ading.ai.hermes.session;

import com.ading.ai.hermes.core.AgentEvent;
import com.ading.ai.hermes.core.FinishReason;
import com.ading.ai.hermes.core.ToolObservation;
import com.ading.ai.hermes.core.ToolRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SessionRestorerTest {

    @Test
    void restoresAgentStateFromPersistedEvents() {
        SessionRestorer restorer = new SessionRestorer();
        SessionRecord record = new SessionRecord(new SessionId("session-1"), List.of(
                AgentEvent.userMessage("read README"),
                AgentEvent.toolRequested(new ToolRequest("call-1", "read_file", Map.of("path", "README.md"))),
                AgentEvent.toolObserved(ToolObservation.success("call-1", "content"))
        ));

        RestoredSession restored = restorer.restore(record);

        assertEquals(3, restored.state().events().size());
        assertEquals(1, restored.state().turnsUsed());
        assertEquals(ResumeDecision.READY_FOR_MODEL, restored.decision());
    }

    @Test
    void detectsPendingToolRequestWithoutReplayingIt() {
        SessionRestorer restorer = new SessionRestorer();
        ToolRequest pending = new ToolRequest("call-1", "read_file", Map.of("path", "README.md"));
        SessionRecord record = new SessionRecord(new SessionId("session-1"), List.of(
                AgentEvent.userMessage("read README"),
                AgentEvent.toolRequested(pending)
        ));

        RestoredSession restored = restorer.restore(record);

        assertEquals(ResumeDecision.PENDING_TOOL_OBSERVATION, restored.decision());
        assertEquals(List.of(pending), restored.pendingToolRequests());
    }

    @Test
    void restoresBatchAsOneModelTurnAndKeepsEveryPendingRequest() {
        SessionRestorer restorer = new SessionRestorer();
        ToolRequest first = new ToolRequest("call-1", "read_file", Map.of("path", "README.md"));
        ToolRequest second = new ToolRequest("call-2", "read_file", Map.of("path", "pom.xml"));
        SessionRecord record = new SessionRecord(new SessionId("session-1"), List.of(
                AgentEvent.userMessage("inspect project"),
                AgentEvent.toolRequested(first),
                AgentEvent.toolRequested(second),
                AgentEvent.toolObserved(ToolObservation.success("call-1", "README"))
        ));

        RestoredSession restored = restorer.restore(record);

        assertEquals(1, restored.state().turnsUsed());
        assertEquals(ResumeDecision.PENDING_TOOL_OBSERVATION, restored.decision());
        assertEquals(List.of(second), restored.pendingToolRequests());
    }

    @Test
    void detectsFinishedSession() {
        SessionRestorer restorer = new SessionRestorer();
        SessionRecord record = new SessionRecord(new SessionId("session-1"), List.of(
                AgentEvent.userMessage("hello"),
                AgentEvent.modelFinalAnswer("done")
        ));

        RestoredSession restored = restorer.restore(record);

        assertEquals(ResumeDecision.FINISHED, restored.decision());
        assertEquals(FinishReason.FINAL_ANSWER, restored.finishReason());
        assertEquals("done", restored.finalAnswer());
    }

    @Test
    void emptySessionStartsReadyForModel() {
        SessionRestorer restorer = new SessionRestorer();

        RestoredSession restored = restorer.restore(new SessionRecord(new SessionId("new"), List.of()));

        assertEquals(ResumeDecision.READY_FOR_MODEL, restored.decision());
        assertEquals(0, restored.state().events().size());
    }
}
