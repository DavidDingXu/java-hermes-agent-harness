package com.dingxu.ai.hermes.core;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorRecoveringAgentLoopTest {

    @Test
    void turnsToolExceptionIntoObservationAndLetsModelRecover() {
        ErrorRecoveringAgentLoop loop = new ErrorRecoveringAgentLoop(
                scriptedModel(
                        ModelTurn.toolRequest(new ToolRequest("call-1", "read_file", Map.of("path", "missing.md"))),
                        ModelTurn.finalAnswer("handled missing file")
                ),
                request -> {
                    throw new IllegalStateException("disk read failed");
                },
                ErrorRecoveryPolicy.maxRecoveries(2)
        );

        AgentRunResult result = loop.run(
                AgentRunRequest.start("read missing file", IterationBudget.maxTurns(4))
        );

        assertEquals(FinishReason.FINAL_ANSWER, result.finishReason());
        assertEquals("handled missing file", result.finalAnswer());
        assertEquals(List.of(
                AgentEventKind.USER_MESSAGE,
                AgentEventKind.TOOL_REQUESTED,
                AgentEventKind.TOOL_OBSERVED,
                AgentEventKind.MODEL_FINAL_ANSWER
        ), result.state().events().stream().map(AgentEvent::kind).toList());
        ToolObservation observation = result.state().events().get(2).toolObservation();
        assertEquals("call-1", observation.callId());
        assertEquals(false, observation.success());
        assertTrue(observation.content().contains("recoverable tool error: IllegalStateException: disk read failed"));
    }

    @Test
    void executesAndRecordsEveryToolCallInOneModelTurn() {
        ToolRequest first = new ToolRequest("call-1", "read_file", Map.of("path", "README.md"));
        ToolRequest second = new ToolRequest("call-2", "read_file", Map.of("path", "pom.xml"));
        ErrorRecoveringAgentLoop loop = new ErrorRecoveringAgentLoop(
                scriptedModel(ModelTurn.toolRequests(List.of(first, second)), ModelTurn.finalAnswer("done")),
                request -> ToolObservation.success(request.callId(), request.name() + " result"),
                ErrorRecoveryPolicy.maxRecoveries(1)
        );

        AgentRunResult result = loop.run(
                AgentRunRequest.start("inspect project", IterationBudget.maxTurns(3))
        );

        assertEquals(List.of(
                AgentEventKind.USER_MESSAGE,
                AgentEventKind.TOOL_REQUESTED,
                AgentEventKind.TOOL_REQUESTED,
                AgentEventKind.TOOL_OBSERVED,
                AgentEventKind.TOOL_OBSERVED,
                AgentEventKind.MODEL_FINAL_ANSWER
        ), result.state().events().stream().map(AgentEvent::kind).toList());
        assertEquals(List.of("call-1", "call-2"), result.state().events().stream()
                .filter(event -> event.kind() == AgentEventKind.TOOL_OBSERVED)
                .map(event -> event.toolObservation().callId())
                .toList());
    }

    @Test
    void recordsModelExceptionAndRetriesWithinRecoveryBudget() {
        AtomicInteger modelCalls = new AtomicInteger();
        ErrorRecoveringAgentLoop loop = new ErrorRecoveringAgentLoop(
                state -> {
                    if (modelCalls.incrementAndGet() == 1) {
                        throw new IllegalStateException("provider timeout");
                    }
                    return ModelTurn.finalAnswer("recovered");
                },
                request -> ToolObservation.success(request.callId(), "unused"),
                ErrorRecoveryPolicy.maxRecoveries(2)
        );

        AgentRunResult result = loop.run(
                AgentRunRequest.start("summarize", IterationBudget.maxTurns(4))
        );

        assertEquals(FinishReason.FINAL_ANSWER, result.finishReason());
        assertEquals("recovered", result.finalAnswer());
        assertEquals(List.of(
                AgentEventKind.USER_MESSAGE,
                AgentEventKind.ERROR_RECOVERED,
                AgentEventKind.MODEL_FINAL_ANSWER
        ), result.state().events().stream().map(AgentEvent::kind).toList());
        assertTrue(result.state().events().get(1).text().contains("model error"));
        assertEquals(2, modelCalls.get());
    }

    @Test
    void stopsWhenModelErrorsExhaustRecoveryBudget() {
        ErrorRecoveringAgentLoop loop = new ErrorRecoveringAgentLoop(
                state -> {
                    throw new IllegalStateException("provider down");
                },
                request -> ToolObservation.success(request.callId(), "unused"),
                ErrorRecoveryPolicy.maxRecoveries(1)
        );

        AgentRunResult result = loop.run(
                AgentRunRequest.start("summarize", IterationBudget.maxTurns(4))
        );

        assertEquals(FinishReason.ERROR_LIMIT, result.finishReason());
        assertEquals("", result.finalAnswer());
        assertEquals(List.of(
                AgentEventKind.USER_MESSAGE,
                AgentEventKind.ERROR_RECOVERED
        ), result.state().events().stream().map(AgentEvent::kind).toList());
        assertTrue(result.state().events().get(1).text().contains("provider down"));
    }

    @Test
    void recordsToolObservationBeforeStoppingAtRecoveryBudget() {
        ErrorRecoveringAgentLoop loop = new ErrorRecoveringAgentLoop(
                scriptedModel(new ToolRequest("call-1", "read_file", Map.of("path", "README.md"))),
                request -> {
                    throw new IllegalStateException("disk unavailable");
                },
                ErrorRecoveryPolicy.maxRecoveries(0)
        );

        AgentRunResult result = loop.run(
                AgentRunRequest.start("read README", IterationBudget.maxTurns(4))
        );

        assertEquals(FinishReason.ERROR_LIMIT, result.finishReason());
        assertEquals(List.of(
                AgentEventKind.USER_MESSAGE,
                AgentEventKind.TOOL_REQUESTED,
                AgentEventKind.TOOL_OBSERVED
        ), result.state().events().stream().map(AgentEvent::kind).toList());
        assertEquals(false, result.state().events().get(2).toolObservation().success());
        assertTrue(result.state().events().get(2).toolObservation().content().contains("disk unavailable"));
    }

    @Test
    void rejectsInvalidRecoveryPolicy() {
        try {
            ErrorRecoveryPolicy.maxRecoveries(-1);
        } catch (IllegalArgumentException error) {
            assertEquals("maxRecoveries must not be negative", error.getMessage());
            return;
        }
        throw new AssertionError("expected IllegalArgumentException");
    }

    private static ModelDriver scriptedModel(ModelTurn... turns) {
        Queue<ModelTurn> queue = new ArrayDeque<>(List.of(turns));
        return state -> queue.remove();
    }

    private static ModelDriver scriptedModel(ToolRequest request) {
        return scriptedModel(ModelTurn.toolRequest(request));
    }
}
