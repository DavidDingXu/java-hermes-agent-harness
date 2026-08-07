package com.dingxu.ai.hermes.core;

import java.util.Objects;

public final class TurnFinalizer {

    public AgentRunResult complete(TurnState state) {
        Objects.requireNonNull(state, "state must not be null");
        if (state.finishReason() == null) {
            throw new IllegalStateException("turn cannot be finalized without a finish reason");
        }
        return new AgentRunResult(
                state.finishReason(),
                state.finalAnswer(),
                state.toAgentState()
        );
    }

    public AgentRunResult stop(TurnState state, FinishReason reason) {
        Objects.requireNonNull(reason, "reason must not be null");
        return complete(state.stop(reason));
    }
}
