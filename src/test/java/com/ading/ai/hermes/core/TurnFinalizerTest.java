package com.ading.ai.hermes.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TurnFinalizerTest {

    private final TurnFinalizer finalizer = new TurnFinalizer();

    @Test
    void completesARecordedFinalAnswerWithoutChangingState() {
        TurnState state = TurnState.start("summarize")
                .recordModelTurn(ModelTurn.finalAnswer("done"));

        AgentRunResult result = finalizer.complete(state);

        assertEquals(FinishReason.FINAL_ANSWER, result.finishReason());
        assertEquals("done", result.finalAnswer());
        assertEquals(state.toAgentState(), result.state());
    }

    @Test
    void stopsAnOpenTurnWithoutInventingAnAnswer() {
        AgentRunResult result = finalizer.stop(
                TurnState.start("keep working"),
                FinishReason.ITERATION_LIMIT
        );

        assertEquals(FinishReason.ITERATION_LIMIT, result.finishReason());
        assertEquals("", result.finalAnswer());
    }

    @Test
    void rejectsAnOpenTurnWithoutAStopReason() {
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> finalizer.complete(TurnState.start("open"))
        );

        assertEquals(
                "turn cannot be finalized without a finish reason",
                error.getMessage()
        );
    }
}
