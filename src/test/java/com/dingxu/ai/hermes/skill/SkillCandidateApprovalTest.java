package com.dingxu.ai.hermes.skill;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillCandidateApprovalTest {

    @Test
    void generatorIgnoresReviewWithoutReusableWorkflow() {
        TaskReview review = new TaskReview(
                "session-001",
                "Fix one typo",
                false,
                List.of("edited README title"),
                List.of(),
                List.of()
        );
        SkillCandidateGenerator generator = new SkillCandidateGenerator();

        assertTrue(generator.generate(review).isEmpty());
    }

    @Test
    void generatorCreatesCandidateFromRecoveredFailureWorkflow() {
        TaskReview review = reusableReview();
        SkillCandidateGenerator generator = new SkillCandidateGenerator();

        SkillCandidate candidate = generator.generate(review).orElseThrow();

        assertEquals("maven-test-recovery", candidate.name());
        assertEquals("Recovered workflow for Maven test failure", candidate.description());
        assertEquals(List.of("maven", "test", "recovery"), candidate.triggers());
        assertTrue(candidate.instructions().contains("Run the narrowest failing test first."));
        assertTrue(candidate.instructions().contains("Check the first compiler error before changing production code."));
        assertEquals(SkillSourceKind.AGENT_CREATED, candidate.provenance().sourceKind());
        assertEquals("review/session-002", candidate.provenance().sourceId());
    }

    @Test
    void approvalFlowDoesNotExposePendingCandidateAsSkill() {
        SkillApprovalFlow flow = new SkillApprovalFlow();
        SkillCandidate candidate = new SkillCandidateGenerator().generate(reusableReview()).orElseThrow();

        PendingSkillCandidate pending = flow.submit(candidate);

        assertEquals(List.of(pending), flow.pending());
        assertTrue(flow.approvedSkills().isEmpty());
    }

    @Test
    void approvalFlowApprovesCandidateIntoLoadableSkill() {
        SkillApprovalFlow flow = new SkillApprovalFlow();
        SkillCandidate candidate = new SkillCandidateGenerator().generate(reusableReview()).orElseThrow();
        PendingSkillCandidate pending = flow.submit(candidate);

        SkillManifest skill = flow.approve(pending.id());

        assertEquals("maven-test-recovery", skill.name());
        assertEquals(candidate.instructions(), skill.instructions());
        assertEquals(SkillSourceKind.LOCAL, skill.provenance().sourceKind());
        assertEquals("approved/" + pending.id(), skill.provenance().sourceId());
        assertTrue(flow.pending().isEmpty());
        assertEquals(List.of(skill), flow.approvedSkills());
    }

    @Test
    void approvalFlowRejectsCandidateWithoutCreatingSkill() {
        SkillApprovalFlow flow = new SkillApprovalFlow();
        PendingSkillCandidate pending = flow.submit(new SkillCandidateGenerator().generate(reusableReview()).orElseThrow());

        flow.reject(pending.id());

        assertTrue(flow.pending().isEmpty());
        assertTrue(flow.approvedSkills().isEmpty());
    }

    @Test
    void approvalFlowRejectsUnknownCandidateId() {
        SkillApprovalFlow flow = new SkillApprovalFlow();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> flow.approve("missing"));

        assertEquals("pending skill candidate not found: missing", error.getMessage());
    }

    private TaskReview reusableReview() {
        return new TaskReview(
                "session-002",
                "Fix Maven test failure",
                true,
                List.of(
                        "Run the narrowest failing test first.",
                        "Check the first compiler error before changing production code.",
                        "Run the full module test after the focused test passes."
                ),
                List.of("Initial full test run hid the real compiler error."),
                List.of("Always inspect the first failing test output before editing production code.")
        );
    }
}
