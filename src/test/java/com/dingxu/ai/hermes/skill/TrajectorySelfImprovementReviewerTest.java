package com.dingxu.ai.hermes.skill;

import com.dingxu.ai.hermes.observability.TraceEvent;
import com.dingxu.ai.hermes.observability.TraceEventKind;
import com.dingxu.ai.hermes.observability.TrajectoryRecord;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrajectorySelfImprovementReviewerTest {

    @Test
    void ignoresSmoothTrajectoryWithoutReusableFailureSignal() {
        TrajectorySelfImprovementReviewer reviewer = new TrajectorySelfImprovementReviewer(new SkillCandidateGenerator());
        TrajectoryRecord trajectory = new TrajectoryRecord(
                "session-1",
                "turn-1",
                "2026-06-20T10:15:30Z",
                List.of(
                        event(TraceEventKind.USER_MESSAGE, Map.of("text", "run maven tests")),
                        event(TraceEventKind.TOOL_REQUESTED, Map.of("toolName", "mvn", "arguments", "test")),
                        event(TraceEventKind.TOOL_OBSERVED, Map.of("success", "true", "content", "BUILD SUCCESS")),
                        event(TraceEventKind.RUN_FINISHED, Map.of("finishReason", "FINAL_ANSWER", "turnsUsed", "2"))
                )
        );

        SelfImprovementReview review = reviewer.review(trajectory);

        assertFalse(review.recoveredFailure());
        assertTrue(review.pendingSkillCandidate().isEmpty());
        assertTrue(review.memoryCandidates().isEmpty());
    }

    @Test
    void createsPendingSkillCandidateFromRecoveredFailureTrajectory() {
        TrajectorySelfImprovementReviewer reviewer = new TrajectorySelfImprovementReviewer(new SkillCandidateGenerator());
        TrajectoryRecord trajectory = new TrajectoryRecord(
                "session-2",
                "turn-1",
                "2026-06-20T10:15:30Z",
                List.of(
                        event(TraceEventKind.USER_MESSAGE, Map.of("text", "Fix Maven test failure")),
                        event(TraceEventKind.ERROR_RECOVERED, Map.of("message", "Initial full test run hid the real compiler error.")),
                        event(TraceEventKind.TOOL_REQUESTED, Map.of(
                                "toolName", "mvn",
                                "arguments", "-Dtest=PaymentServiceTest test"
                        )),
                        event(TraceEventKind.TOOL_OBSERVED, Map.of(
                                "success", "true",
                                "content", "PaymentServiceTest passed after checking the first compiler error."
                        )),
                        event(TraceEventKind.RUN_FINISHED, Map.of("finishReason", "FINAL_ANSWER", "turnsUsed", "3"))
                )
        );

        SelfImprovementReview review = reviewer.review(trajectory);

        assertTrue(review.recoveredFailure());
        SkillCandidate candidate = review.pendingSkillCandidate().orElseThrow();
        assertEquals("maven-test-recovery", candidate.name());
        assertEquals(SkillSourceKind.AGENT_CREATED, candidate.provenance().sourceKind());
        assertEquals("review/session-2", candidate.provenance().sourceId());
        assertTrue(candidate.instructions().contains("Initial full test run hid the real compiler error."));
        assertTrue(candidate.instructions().contains("-Dtest=PaymentServiceTest test"));
        assertTrue(review.memoryCandidates().isEmpty());
    }

    @Test
    void extractsUserPreferenceAsMemoryCandidateNotSkillCandidate() {
        TrajectorySelfImprovementReviewer reviewer = new TrajectorySelfImprovementReviewer(new SkillCandidateGenerator());
        TrajectoryRecord trajectory = new TrajectoryRecord(
                "session-3",
                "turn-1",
                "2026-06-20T10:15:30Z",
                List.of(
                        event(TraceEventKind.USER_MESSAGE, Map.of("text", "希望你先给结论，再解释原因")),
                        event(TraceEventKind.RUN_FINISHED, Map.of("finishReason", "FINAL_ANSWER", "turnsUsed", "1"))
                )
        );

        SelfImprovementReview review = reviewer.review(trajectory);

        assertTrue(review.pendingSkillCandidate().isEmpty());
        assertEquals(1, review.memoryCandidates().size());
        assertEquals("希望你先给结论，再解释原因", review.memoryCandidates().get(0).text());
    }

    @Test
    void reviewToolPolicyAllowsOnlyMemoryAndSkillTools() {
        ReviewToolPolicy policy = ReviewToolPolicy.memoryAndSkillsOnly();

        assertTrue(policy.allows("memory"));
        assertTrue(policy.allows("skill_manage"));
        assertTrue(policy.allows("skill_view"));
        assertTrue(policy.allows("skills_list"));

        assertFalse(policy.allows("terminal"));
        assertFalse(policy.allows("delegate_task"));
        assertFalse(policy.allows("web_search"));
    }

    private TraceEvent event(TraceEventKind kind, Map<String, String> attributes) {
        return new TraceEvent(kind, "session", "turn", "", "", "2026-06-20T10:15:30Z", attributes);
    }
}
