package com.dingxu.ai.hermes.verification;

import com.dingxu.ai.hermes.core.AgentRunResult;
import com.dingxu.ai.hermes.core.FinishReason;
import java.util.Objects;

public final class CompletionGate {

    private final CompletionVerifier verifier;

    public CompletionGate(CompletionVerifier verifier) {
        this.verifier = Objects.requireNonNull(verifier, "verifier must not be null");
    }

    public CompletionDecision evaluate(AgentRunResult result) {
        Objects.requireNonNull(result, "result must not be null");
        if (result.finishReason() != FinishReason.FINAL_ANSWER) {
            return CompletionDecision.notEligible("run did not reach a final answer");
        }
        return CompletionDecision.from(Objects.requireNonNull(
                verifier.verify(result),
                "verifier result must not be null"
        ));
    }
}
