package com.ading.ai.hermes.verification;

import com.ading.ai.hermes.core.AgentRunResult;
import com.ading.ai.hermes.core.AgentState;
import com.ading.ai.hermes.core.FinishReason;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompletionGateTest {

    @Test
    void rejectsFinalAnswerWhenRequiredEvidenceIsMissing() {
        CompletionGate gate = new CompletionGate(result -> CompletionEvidence.reject("tests were not run"));

        CompletionDecision decision = gate.evaluate(new AgentRunResult(
                FinishReason.FINAL_ANSWER,
                "done",
                AgentState.start("fix bug")
        ));

        assertTrue(decision.eligible());
        assertFalse(decision.accepted());
        assertEquals("tests were not run", decision.detail());
    }

    @Test
    void doesNotVerifyRunsThatHaveNotReachedFinalAnswer() {
        CompletionGate gate = new CompletionGate(result -> {
            throw new AssertionError("verifier must not be called");
        });

        CompletionDecision decision = gate.evaluate(new AgentRunResult(
                FinishReason.ITERATION_LIMIT,
                "",
                AgentState.start("fix bug")
        ));

        assertFalse(decision.eligible());
        assertFalse(decision.accepted());
        assertEquals("run did not reach a final answer", decision.detail());
    }
}
