package com.ading.ai.hermes.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InterruptibleAgentLoopTest {

    @Test
    void stopsAfterToolObservationAtSafeBoundary() {
        ManualStopSignal stopSignal = new ManualStopSignal();
        AtomicInteger modelCalls = new AtomicInteger();
        InterruptibleAgentLoop loop = new InterruptibleAgentLoop(
                scriptedModel(modelCalls,
                        ModelTurn.toolRequest(new ToolRequest("call-1", "read_file", Map.of("path", "README.md"))),
                        ModelTurn.finalAnswer("this should not be reached")
                ),
                request -> {
                    stopSignal.requestStop("user sent /stop");
                    return ToolObservation.success(request.callId(), "README content");
                },
                stopSignal
        );

        AgentRunResult result = loop.run(
                AgentRunRequest.start("read README", IterationBudget.maxTurns(4))
        );

        assertEquals(FinishReason.INTERRUPTED, result.finishReason());
        assertEquals("", result.finalAnswer());
        assertEquals(1, result.state().turnsUsed());
        assertEquals(1, modelCalls.get());
        assertEquals(List.of(
                AgentEvent.userMessage("read README"),
                AgentEvent.toolRequested(new ToolRequest("call-1", "read_file", Map.of("path", "README.md"))),
                AgentEvent.toolObserved(ToolObservation.success("call-1", "README content")),
                AgentEvent.runInterrupted("user sent /stop")
        ), result.state().events());
    }

    @Test
    void completesWholeToolBatchBeforeStoppingAtSafeBoundary() {
        ManualStopSignal stopSignal = new ManualStopSignal();
        ToolRequest first = new ToolRequest("call-1", "read_file", Map.of("path", "README.md"));
        ToolRequest second = new ToolRequest("call-2", "read_file", Map.of("path", "pom.xml"));
        AtomicInteger executed = new AtomicInteger();
        InterruptibleAgentLoop loop = new InterruptibleAgentLoop(
                scriptedModel(new AtomicInteger(), ModelTurn.toolRequests(List.of(first, second))),
                request -> {
                    if (executed.incrementAndGet() == 1) {
                        stopSignal.requestStop("user sent /stop");
                    }
                    return ToolObservation.success(request.callId(), "content");
                },
                stopSignal
        );

        AgentRunResult result = loop.run(
                AgentRunRequest.start("inspect project", IterationBudget.maxTurns(2))
        );

        assertEquals(FinishReason.INTERRUPTED, result.finishReason());
        assertEquals(2, executed.get());
        assertEquals(List.of("call-1", "call-2"), result.state().events().stream()
                .filter(event -> event.kind() == AgentEventKind.TOOL_OBSERVED)
                .map(event -> event.toolObservation().callId())
                .toList());
    }

    @Test
    void preservesReturnedModelDecisionWhenStopArrivesDuringTheModelCall() {
        ManualStopSignal stopSignal = new ManualStopSignal();
        AtomicInteger toolCalls = new AtomicInteger();
        ToolRequest request = new ToolRequest(
                "call-1",
                "edit_file",
                Map.of("path", "README.md")
        );
        InterruptibleAgentLoop loop = new InterruptibleAgentLoop(
                state -> {
                    stopSignal.requestStop("user sent /stop");
                    return ModelTurn.toolRequest(request);
                },
                toolRequest -> {
                    toolCalls.incrementAndGet();
                    return ToolObservation.success(toolRequest.callId(), "edited");
                },
                stopSignal
        );

        AgentRunResult result = loop.run(
                AgentRunRequest.start("edit README", IterationBudget.maxTurns(2))
        );

        assertEquals(FinishReason.INTERRUPTED, result.finishReason());
        assertEquals(0, toolCalls.get());
        assertEquals(List.of(
                AgentEvent.userMessage("edit README"),
                AgentEvent.toolRequested(request),
                AgentEvent.runInterrupted("user sent /stop")
        ), result.state().events());
    }

    private static ModelDriver scriptedModel(AtomicInteger modelCalls, ModelTurn... turns) {
        Queue<ModelTurn> queue = new ArrayDeque<>(List.of(turns));
        return state -> {
            modelCalls.incrementAndGet();
            if (queue.isEmpty()) {
                return ModelTurn.finalAnswer("fallback");
            }
            return queue.remove();
        };
    }
}
