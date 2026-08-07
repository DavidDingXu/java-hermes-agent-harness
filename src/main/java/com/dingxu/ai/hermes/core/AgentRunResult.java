package com.dingxu.ai.hermes.core;

import java.util.Objects;

public record AgentRunResult(FinishReason finishReason, String finalAnswer, AgentState state) {

    public AgentRunResult {
        Objects.requireNonNull(finishReason, "finishReason must not be null");
        finalAnswer = finalAnswer == null ? "" : finalAnswer;
        Objects.requireNonNull(state, "state must not be null");
    }
}
