package com.dingxu.ai.hermes.skill;

import com.dingxu.ai.hermes.memory.MemoryWriteResult;
import java.util.List;
import java.util.Objects;

public record SelfImprovementResult(
        SelfImprovementReview review,
        List<MemoryWriteResult> memoryWrites,
        List<PendingSkillCandidate> pendingSkills
) {

    public SelfImprovementResult {
        Objects.requireNonNull(review, "review must not be null");
        Objects.requireNonNull(memoryWrites, "memoryWrites must not be null");
        Objects.requireNonNull(pendingSkills, "pendingSkills must not be null");
        memoryWrites = List.copyOf(memoryWrites);
        pendingSkills = List.copyOf(pendingSkills);
    }
}
