package com.dingxu.ai.hermes.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLoopTest {

    @Test
    void finishesWhenModelReturnsFinalAnswer() {
        AgentLoop loop = new AgentLoop(
                scriptedModel(ModelTurn.finalAnswer("done")),
                request -> ToolObservation.success(request.callId(), "unused")
        );

        AgentRunResult result = loop.run(
                AgentRunRequest.start("summarize this", IterationBudget.maxTurns(3))
        );

        assertEquals(FinishReason.FINAL_ANSWER, result.finishReason());
        assertEquals("done", result.finalAnswer());
        assertEquals(1, result.state().turnsUsed());
        assertEquals(List.of(
                AgentEvent.userMessage("summarize this"),
                AgentEvent.modelFinalAnswer("done")
        ), result.state().events());
    }

    @Test
    void feedsToolObservationBackIntoNextModelTurn() {
        AgentLoop loop = new AgentLoop(
                scriptedModel(
                        ModelTurn.toolRequest(new ToolRequest("call-1", "read_file", Map.of("path", "README.md"))),
                        ModelTurn.finalAnswer("file read")
                ),
                request -> ToolObservation.success(request.callId(), "README content")
        );

        AgentRunResult result = loop.run(
                AgentRunRequest.start("read README", IterationBudget.maxTurns(4))
        );

        assertEquals(FinishReason.FINAL_ANSWER, result.finishReason());
        assertEquals("file read", result.finalAnswer());
        assertEquals(2, result.state().turnsUsed());
        assertTrue(result.state().events().contains(
                AgentEvent.toolRequested(new ToolRequest("call-1", "read_file", Map.of("path", "README.md")))
        ));
        assertTrue(result.state().events().contains(
                AgentEvent.toolObserved(ToolObservation.success("call-1", "README content"))
        ));
    }

    @Test
    void recordsAllToolCallsAndObservationsFromOneModelTurn() {
        AgentLoop loop = new AgentLoop(
                scriptedModel(
                        ModelTurn.toolRequests(List.of(
                                new ToolRequest("call-1", "read_file", Map.of("path", "README.md")),
                                new ToolRequest("call-2", "read_file", Map.of("path", "pom.xml"))
                        )),
                        ModelTurn.finalAnswer("both files read")
                ),
                request -> ToolObservation.success(request.callId(), "content:" + request.arguments().get("path"))
        );

        AgentRunResult result = loop.run(
                AgentRunRequest.start("read project files", IterationBudget.maxTurns(3))
        );

        assertEquals(List.of("call-1", "call-2"), result.state().events().stream()
                .filter(event -> event.kind() == AgentEventKind.TOOL_OBSERVED)
                .map(event -> event.toolObservation().callId())
                .toList());
        assertEquals("both files read", result.finalAnswer());
        assertEquals(2, result.state().turnsUsed());
    }

    @Test
    void stopsWhenIterationBudgetIsExhausted() {
        AgentLoop loop = new AgentLoop(
                scriptedModel(ModelTurn.toolRequest(new ToolRequest("call-1", "search", Map.of("query", "Hermes")))),
                request -> ToolObservation.success(request.callId(), "result")
        );

        AgentRunResult result = loop.run(
                AgentRunRequest.start("keep searching", IterationBudget.maxTurns(1))
        );

        assertEquals(FinishReason.ITERATION_LIMIT, result.finishReason());
        assertEquals("", result.finalAnswer());
        assertEquals(1, result.state().turnsUsed());
    }

    private static ModelDriver scriptedModel(ModelTurn... turns) {
        Queue<ModelTurn> queue = new ArrayDeque<>(List.of(turns));
        return state -> {
            if (queue.isEmpty()) {
                return ModelTurn.finalAnswer("fallback");
            }
            return queue.remove();
        };
    }
}
