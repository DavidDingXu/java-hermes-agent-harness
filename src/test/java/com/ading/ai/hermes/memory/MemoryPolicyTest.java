package com.ading.ai.hermes.memory;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MemoryPolicyTest {

    @Test
    void acceptsUserPreferenceIntoUserProfile() {
        MemoryPolicy policy = MemoryPolicy.defaultPolicy();
        MemoryCandidate candidate = MemoryCandidate.fromUserText("我以后都希望你先给结论，再解释原因。");

        MemoryDecision decision = policy.evaluate(candidate);

        assertEquals(MemoryDecisionKind.ACCEPT, decision.kind());
        assertEquals(MemoryTarget.USER, decision.target());
        assertEquals("user_preference", decision.reason());
        assertEquals("User prefers answers that give the conclusion first, then explain the reason.", decision.normalizedContent());
    }

    @Test
    void acceptsStableProjectFactIntoAgentMemory() {
        MemoryPolicy policy = MemoryPolicy.defaultPolicy();
        MemoryCandidate candidate = MemoryCandidate.fromObservation(
                "Project java-hermes-agent-harness uses Maven and Java 21."
        );

        MemoryDecision decision = policy.evaluate(candidate);

        assertEquals(MemoryDecisionKind.ACCEPT, decision.kind());
        assertEquals(MemoryTarget.MEMORY, decision.target());
        assertEquals("stable_project_fact", decision.reason());
        assertEquals("Project java-hermes-agent-harness uses Maven and Java 21.", decision.normalizedContent());
    }

    @Test
    void rejectsTemporaryTaskProgress() {
        MemoryPolicy policy = MemoryPolicy.defaultPolicy();
        MemoryCandidate candidate = MemoryCandidate.fromObservation(
                "Completed the temporary migration and uploaded report-42."
        );

        MemoryDecision decision = policy.evaluate(candidate);

        assertEquals(MemoryDecisionKind.REJECT, decision.kind());
        assertEquals("task_progress_is_session_history", decision.reason());
    }

    @Test
    void rejectsTodoState() {
        MemoryPolicy policy = MemoryPolicy.defaultPolicy();
        MemoryCandidate candidate = MemoryCandidate.fromObservation(
                "TODO: tomorrow continue the one-time migration."
        );

        MemoryDecision decision = policy.evaluate(candidate);

        assertEquals(MemoryDecisionKind.REJECT, decision.kind());
        assertEquals("todo_belongs_to_session", decision.reason());
    }

    @Test
    void rejectsSensitiveContent() {
        MemoryPolicy policy = MemoryPolicy.defaultPolicy();
        MemoryCandidate candidate = MemoryCandidate.fromUserText("Remember my API key is sk-test-123456.");

        MemoryDecision decision = policy.evaluate(candidate);

        assertEquals(MemoryDecisionKind.REJECT, decision.kind());
        assertEquals("sensitive_content", decision.reason());
    }

    @Test
    void rejectsVagueLowSignalContent() {
        MemoryPolicy policy = MemoryPolicy.defaultPolicy();
        MemoryCandidate candidate = MemoryCandidate.fromObservation("User asked about Java.");

        MemoryDecision decision = policy.evaluate(candidate);

        assertEquals(MemoryDecisionKind.REJECT, decision.kind());
        assertEquals("low_signal", decision.reason());
    }

    @Test
    void storeAddsAcceptedMemoriesAndSkipsRejectedOnes() {
        MemoryPolicy policy = MemoryPolicy.defaultPolicy();
        MemoryStore store = new MemoryStore(policy, 200, 200);

        MemoryWriteResult accepted = store.consider(MemoryCandidate.fromObservation(
                "Project java-hermes-agent-harness uses Maven and Java 21."
        ));
        MemoryWriteResult rejected = store.consider(MemoryCandidate.fromObservation(
                "Completed the temporary migration and uploaded report-42."
        ));

        assertEquals(true, accepted.written());
        assertEquals(false, rejected.written());
        assertEquals(List.of("Project java-hermes-agent-harness uses Maven and Java 21."), store.entries(MemoryTarget.MEMORY));
        assertEquals(List.of(), store.entries(MemoryTarget.USER));
    }

    @Test
    void storeRejectsDuplicates() {
        MemoryStore store = new MemoryStore(MemoryPolicy.defaultPolicy(), 200, 200);

        MemoryWriteResult first = store.consider(MemoryCandidate.fromObservation(
                "Project java-hermes-agent-harness uses Maven and Java 21."
        ));
        MemoryWriteResult second = store.consider(MemoryCandidate.fromObservation(
                "Project java-hermes-agent-harness uses Maven and Java 21."
        ));

        assertEquals(true, first.written());
        assertEquals(false, second.written());
        assertEquals("duplicate", second.decision().reason());
    }

    @Test
    void storeRejectsEntriesThatExceedTargetLimit() {
        MemoryStore store = new MemoryStore(MemoryPolicy.defaultPolicy(), 20, 20);

        MemoryWriteResult result = store.consider(MemoryCandidate.fromObservation(
                "Project java-hermes-agent-harness uses Maven and Java 21."
        ));

        assertEquals(false, result.written());
        assertEquals("memory_limit_exceeded", result.decision().reason());
    }
}
