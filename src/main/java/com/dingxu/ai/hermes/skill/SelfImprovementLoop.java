package com.dingxu.ai.hermes.skill;

import com.dingxu.ai.hermes.memory.MemoryCandidate;
import com.dingxu.ai.hermes.memory.MemoryStore;
import com.dingxu.ai.hermes.memory.MemoryWriteResult;
import com.dingxu.ai.hermes.observability.TrajectoryRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SelfImprovementLoop {

    private final TrajectorySelfImprovementReviewer reviewer;
    private final MemoryStore memoryStore;
    private final SkillApprovalFlow skillApprovalFlow;

    public SelfImprovementLoop(
            TrajectorySelfImprovementReviewer reviewer,
            MemoryStore memoryStore,
            SkillApprovalFlow skillApprovalFlow
    ) {
        this.reviewer = Objects.requireNonNull(reviewer, "reviewer must not be null");
        this.memoryStore = Objects.requireNonNull(memoryStore, "memoryStore must not be null");
        this.skillApprovalFlow = Objects.requireNonNull(skillApprovalFlow, "skillApprovalFlow must not be null");
    }

    public SelfImprovementResult review(TrajectoryRecord trajectory) {
        SelfImprovementReview review = reviewer.review(trajectory);
        List<MemoryWriteResult> writes = new ArrayList<>();
        for (MemoryCandidate candidate : review.memoryCandidates()) {
            writes.add(memoryStore.consider(candidate));
        }
        List<PendingSkillCandidate> pendingSkills = new ArrayList<>();
        review.pendingSkillCandidate()
                .map(skillApprovalFlow::submit)
                .ifPresent(pendingSkills::add);
        return new SelfImprovementResult(review, writes, pendingSkills);
    }
}
