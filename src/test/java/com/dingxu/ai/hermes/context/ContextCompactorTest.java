package com.dingxu.ai.hermes.context;

import com.dingxu.ai.hermes.core.AgentEvent;
import com.dingxu.ai.hermes.core.AgentEventKind;
import com.dingxu.ai.hermes.core.AgentState;
import com.dingxu.ai.hermes.core.ToolObservation;
import com.dingxu.ai.hermes.core.ToolRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextCompactorTest {

    @Test
    void keepsStateUnchangedWhenItFitsTheBudget() {
        ContextCompactor compactor = new ContextCompactor(
                new ContextCompactionPolicy(500, 1, 2, 200, 80)
        );
        AgentState state = new AgentState(List.of(
                AgentEvent.userMessage("read README"),
                AgentEvent.modelFinalAnswer("README is short")
        ), 1);

        ContextCompactionResult result = compactor.compact(state);

        assertFalse(result.compacted());
        assertEquals(state, result.state());
        assertEquals(0, result.report().summarizedEvents());
    }

    @Test
    void compactsMiddleEventsAndKeepsHeadAndTail() {
        ContextCompactor compactor = new ContextCompactor(
                new ContextCompactionPolicy(120, 1, 3, 320, 80)
        );
        AgentState state = new AgentState(List.of(
                AgentEvent.userMessage("inspect the workspace"),
                AgentEvent.toolRequested(new ToolRequest("call-old", "read_file", Map.of("path", "large.log"))),
                AgentEvent.toolObserved(ToolObservation.success("call-old", "old output ".repeat(30))),
                AgentEvent.modelFinalAnswer("old branch analysis"),
                AgentEvent.userMessage("now fix the failing test"),
                AgentEvent.toolRequested(new ToolRequest("call-new", "search_files", Map.of("pattern", "failure"))),
                AgentEvent.toolObserved(ToolObservation.success("call-new", "fresh failing test result"))
        ), 3);

        ContextCompactionResult result = compactor.compact(state);

        assertTrue(result.compacted());
        assertEquals(5, result.state().events().size());
        assertEquals(3, result.state().turnsUsed());
        assertEquals(AgentEventKind.USER_MESSAGE, result.state().events().get(0).kind());
        assertEquals(AgentEventKind.CONTEXT_SUMMARY, result.state().events().get(1).kind());
        assertEquals(AgentEventKind.USER_MESSAGE, result.state().events().get(2).kind());
        assertEquals(AgentEventKind.TOOL_REQUESTED, result.state().events().get(3).kind());
        assertEquals(AgentEventKind.TOOL_OBSERVED, result.state().events().get(4).kind());
        assertEquals(3, result.report().summarizedEvents());
        assertTrue(result.state().events().get(1).text().contains("REFERENCE ONLY"));
        assertTrue(result.state().events().get(1).text().contains("read_file"));
        assertTrue(result.state().events().get(1).text().contains("latest user message wins"));
    }

    @Test
    void doesNotSplitToolRequestFromObservedTail() {
        ContextCompactor compactor = new ContextCompactor(
                new ContextCompactionPolicy(90, 1, 2, 240, 80)
        );
        AgentState state = new AgentState(List.of(
                AgentEvent.userMessage("start"),
                AgentEvent.modelFinalAnswer("old answer ".repeat(12)),
                AgentEvent.toolRequested(new ToolRequest("call-1", "read_file", Map.of("path", "README.md"))),
                AgentEvent.toolObserved(ToolObservation.success("call-1", "fresh read result")),
                AgentEvent.userMessage("continue from the file result")
        ), 2);

        ContextCompactionResult result = compactor.compact(state);

        assertTrue(result.compacted());
        assertEquals(List.of(
                AgentEventKind.USER_MESSAGE,
                AgentEventKind.CONTEXT_SUMMARY,
                AgentEventKind.TOOL_REQUESTED,
                AgentEventKind.TOOL_OBSERVED,
                AgentEventKind.USER_MESSAGE
        ), result.state().events().stream().map(AgentEvent::kind).toList());
        assertEquals("call-1", result.state().events().get(2).toolRequest().callId());
        assertEquals("call-1", result.state().events().get(3).toolObservation().callId());
        assertEquals(1, result.report().summarizedEvents());
    }

    @Test
    void doesNotSplitABatchOfToolRequestsAndObservations() {
        ContextCompactor compactor = new ContextCompactor(
                new ContextCompactionPolicy(90, 1, 3, 240, 80)
        );
        AgentState state = new AgentState(List.of(
                AgentEvent.userMessage("start"),
                AgentEvent.modelFinalAnswer("old answer ".repeat(12)),
                AgentEvent.toolRequested(new ToolRequest("call-1", "read_file", Map.of("path", "a.txt"))),
                AgentEvent.toolRequested(new ToolRequest("call-2", "read_file", Map.of("path", "b.txt"))),
                AgentEvent.toolObserved(ToolObservation.success("call-1", "a")),
                AgentEvent.toolObserved(ToolObservation.success("call-2", "b")),
                AgentEvent.userMessage("compare both files")
        ), 2);

        ContextCompactionResult result = compactor.compact(state);

        assertTrue(result.compacted());
        assertEquals(List.of(
                AgentEventKind.USER_MESSAGE,
                AgentEventKind.CONTEXT_SUMMARY,
                AgentEventKind.TOOL_REQUESTED,
                AgentEventKind.TOOL_REQUESTED,
                AgentEventKind.TOOL_OBSERVED,
                AgentEventKind.TOOL_OBSERVED,
                AgentEventKind.USER_MESSAGE
        ), result.state().events().stream().map(AgentEvent::kind).toList());
        assertEquals(1, result.report().summarizedEvents());
    }

    @Test
    void keepsInterruptedReasonInsideCompactionSummary() {
        ContextCompactor compactor = new ContextCompactor(
                new ContextCompactionPolicy(120, 1, 2, 360, 80)
        );
        AgentState state = new AgentState(List.of(
                AgentEvent.userMessage("scan repository"),
                AgentEvent.runInterrupted("user stopped full repository scan"),
                AgentEvent.modelFinalAnswer("old scan result ".repeat(20)),
                AgentEvent.userMessage("continue with payment module"),
                AgentEvent.toolRequested(new ToolRequest("call-2", "list_files", Map.of("path", "payment"))),
                AgentEvent.toolObserved(ToolObservation.success("call-2", "payment files"))
        ), 2);

        ContextCompactionResult result = compactor.compact(state);

        assertTrue(result.compacted());
        assertTrue(result.state().events().get(1).text().contains("run interrupted"));
        assertTrue(result.state().events().get(1).text().contains("user stopped full repository scan"));
    }

    @Test
    void rejectsInvalidPolicy() {
        assertEquals("maxCharacters must be positive", illegalArgumentMessage(() ->
                new ContextCompactionPolicy(0, 1, 1, 100, 50)
        ));
        assertEquals("protected event counts must not be negative", illegalArgumentMessage(() ->
                new ContextCompactionPolicy(100, -1, 1, 100, 50)
        ));
        assertEquals("summaryMaxCharacters must be positive", illegalArgumentMessage(() ->
                new ContextCompactionPolicy(100, 1, 1, 0, 50)
        ));
        assertEquals("eventPreviewMaxCharacters must be positive", illegalArgumentMessage(() ->
                new ContextCompactionPolicy(100, 1, 1, 100, 0)
        ));
    }

    private String illegalArgumentMessage(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException error) {
            return error.getMessage();
        }
        throw new AssertionError("expected IllegalArgumentException");
    }
}
