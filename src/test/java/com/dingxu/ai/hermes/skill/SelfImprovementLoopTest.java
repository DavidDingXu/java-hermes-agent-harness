package com.dingxu.ai.hermes.skill;

import com.dingxu.ai.hermes.memory.MemoryPolicy;
import com.dingxu.ai.hermes.memory.MemoryStore;
import com.dingxu.ai.hermes.memory.MemoryTarget;
import com.dingxu.ai.hermes.observability.TraceEvent;
import com.dingxu.ai.hermes.observability.TraceEventKind;
import com.dingxu.ai.hermes.observability.TrajectoryRecord;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelfImprovementLoopTest {

    @Test
    void writesAcceptedMemoryAndSubmitsSkillCandidateForApproval() {
        MemoryStore memoryStore = new MemoryStore(MemoryPolicy.defaultPolicy(), 4096, 4096);
        SkillApprovalFlow approvalFlow = new SkillApprovalFlow();
        SelfImprovementLoop loop = new SelfImprovementLoop(
                new TrajectorySelfImprovementReviewer(new SkillCandidateGenerator()),
                memoryStore,
                approvalFlow
        );
        TrajectoryRecord trajectory = new TrajectoryRecord(
                "session-9",
                "turn-1",
                "2026-06-20T10:15:30Z",
                List.of(
                        event(TraceEventKind.USER_MESSAGE, Map.of("text", "希望你先给结论，再解释原因")),
                        event(TraceEventKind.USER_MESSAGE, Map.of("text", "Fix Maven test failure")),
                        event(TraceEventKind.ERROR_RECOVERED, Map.of("message", "Initial full test run hid the real compiler error.")),
                        event(TraceEventKind.TOOL_REQUESTED, Map.of("toolName", "mvn", "arguments", "-Dtest=PaymentServiceTest test")),
                        event(TraceEventKind.TOOL_OBSERVED, Map.of("success", "true", "content", "PaymentServiceTest passed.")),
                        event(TraceEventKind.RUN_FINISHED, Map.of("finishReason", "FINAL_ANSWER", "turnsUsed", "3"))
                )
        );

        SelfImprovementResult result = loop.review(trajectory);

        assertEquals(1, result.memoryWrites().size());
        assertTrue(result.memoryWrites().get(0).written());
        assertEquals(List.of("User prefers answers that give the conclusion first, then explain the reason."),
                memoryStore.entries(MemoryTarget.USER));
        assertEquals(1, result.pendingSkills().size());
        assertEquals(result.pendingSkills(), approvalFlow.pending());
        assertTrue(approvalFlow.approvedSkills().isEmpty());
    }

    private TraceEvent event(TraceEventKind kind, Map<String, String> attributes) {
        return new TraceEvent(kind, "session", "turn", "", "", "2026-06-20T10:15:30Z", attributes);
    }
}
