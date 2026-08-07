package com.dingxu.ai.hermes.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TurnStateTest {

    @Test
    void recordsOneTurnInOrder() {
        TurnState state = TurnState.start("fix the failing test")
                .recordModelTurn(ModelTurn.toolRequest(new ToolRequest("call-1", "search_files", Map.of("query", "TODO"))))
                .recordToolObservation(ToolObservation.success("call-1", "src/App.java"))
                .recordModelTurn(ModelTurn.finalAnswer("fixed"));

        assertEquals("fix the failing test", state.userMessage());
        assertEquals(2, state.modelTurns());
        assertEquals(FinishReason.FINAL_ANSWER, state.finishReason());
        assertEquals("fixed", state.finalAnswer());
        assertEquals(List.of(
                AgentEvent.userMessage("fix the failing test"),
                AgentEvent.toolRequested(new ToolRequest("call-1", "search_files", Map.of("query", "TODO"))),
                AgentEvent.toolObserved(ToolObservation.success("call-1", "src/App.java")),
                AgentEvent.modelFinalAnswer("fixed")
        ), state.events());
    }

    @Test
    void rejectsObservationWithoutPendingToolCall() {
        TurnState state = TurnState.start("read file");

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> state.recordToolObservation(ToolObservation.success("call-1", "content"))
        );

        assertEquals("cannot record tool observation without a pending tool call", error.getMessage());
    }

    @Test
    void rejectsMismatchedToolObservation() {
        TurnState state = TurnState.start("read file")
                .recordModelTurn(ModelTurn.toolRequest(new ToolRequest("call-1", "read_file", Map.of("path", "README.md"))));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> state.recordToolObservation(ToolObservation.success("call-2", "content"))
        );

        assertEquals("tool observation callId does not match pending tool call: call-1", error.getMessage());
    }

    @Test
    void marksIterationLimitWithoutInventingFinalAnswer() {
        TurnState state = TurnState.start("keep searching")
                .recordModelTurn(ModelTurn.toolRequest(new ToolRequest("call-1", "search", Map.of("query", "Hermes"))))
                .recordToolObservation(ToolObservation.success("call-1", "result"))
                .stop(FinishReason.ITERATION_LIMIT);

        assertEquals(FinishReason.ITERATION_LIMIT, state.finishReason());
        assertEquals("", state.finalAnswer());
        assertEquals(1, state.modelTurns());
    }
}
