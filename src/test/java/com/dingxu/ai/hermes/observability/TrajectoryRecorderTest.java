package com.dingxu.ai.hermes.observability;

import com.dingxu.ai.hermes.core.AgentEvent;
import com.dingxu.ai.hermes.core.AgentRunResult;
import com.dingxu.ai.hermes.core.AgentState;
import com.dingxu.ai.hermes.core.FinishReason;
import com.dingxu.ai.hermes.core.ToolObservation;
import com.dingxu.ai.hermes.core.ToolRequest;
import com.dingxu.ai.hermes.delegate.DelegationResult;
import com.dingxu.ai.hermes.delegate.DelegationStatus;
import com.dingxu.ai.hermes.delegate.SubAgentResult;
import com.dingxu.ai.hermes.core.IterationBudget;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrajectoryRecorderTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-20T10:15:30Z"), ZoneOffset.UTC);

    @Test
    void recordsRunEventsWithCorrelationIdsAndRedactedPayloads() {
        TrajectoryRecorder recorder = new TrajectoryRecorder(clock, new TraceRedactor());
        AgentRunResult runResult = new AgentRunResult(
                FinishReason.FINAL_ANSWER,
                "done",
                new AgentState(List.of(
                        AgentEvent.userMessage("call payment api with token sk-secret123"),
                        AgentEvent.errorRecovered("retry after apiKey=sk-error123"),
                        AgentEvent.toolRequested(new ToolRequest("call-1", "http_get", Map.of(
                                "url", "https://example.com?password=abc",
                                "apiKey", "sk-secret456"
                        ))),
                        AgentEvent.toolObserved(ToolObservation.success("call-1", "Authorization: Bearer secret-token")),
                        AgentEvent.modelFinalAnswer("done")
                ), 2)
        );

        TrajectoryRecord record = recorder.recordRun("session-1", "turn-1", runResult);

        assertEquals("session-1", record.sessionId());
        assertEquals("turn-1", record.turnId());
        assertEquals("2026-06-20T10:15:30Z", record.createdAt());
        assertEquals(List.of(
                TraceEventKind.USER_MESSAGE,
                TraceEventKind.ERROR_RECOVERED,
                TraceEventKind.TOOL_REQUESTED,
                TraceEventKind.TOOL_OBSERVED,
                TraceEventKind.MODEL_FINAL_ANSWER,
                TraceEventKind.RUN_FINISHED
        ), record.events().stream().map(TraceEvent::kind).toList());
        assertTrue(record.events().get(1).attributes().get("message").contains("[REDACTED]"));
        assertTrue(record.events().get(2).attributes().get("arguments").contains("[REDACTED]"));
        assertFalse(record.events().get(2).attributes().get("arguments").contains("sk-secret456"));
        assertTrue(record.events().get(3).attributes().get("content").contains("Bearer [REDACTED]"));
    }

    @Test
    void recordsDelegationResultsWithoutChildConversationEvents() {
        TrajectoryRecorder recorder = new TrajectoryRecorder(clock, new TraceRedactor());
        DelegationResult delegationResult = new DelegationResult(List.of(
                new SubAgentResult(
                        "task-1",
                        DelegationStatus.COMPLETED,
                        "payment tests passed",
                        FinishReason.FINAL_ANSWER,
                        2,
                        IterationBudget.maxTurns(4),
                        List.of("file")
                )
        ));

        TrajectoryRecord record = recorder.recordDelegation("parent-session", "parent-turn", delegationResult);

        assertEquals(1, record.events().size());
        TraceEvent event = record.events().get(0);
        assertEquals(TraceEventKind.SUBAGENT_STOP, event.kind());
        assertEquals("task-1", event.taskId());
        assertEquals("parent-turn", event.parentTurnId());
        assertEquals("payment tests passed", event.attributes().get("summary"));
        assertEquals("2", event.attributes().get("turnsUsed"));
        assertFalse(event.attributes().containsKey("childEvents"));
    }
}
